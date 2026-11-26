package com.playmonumenta.redissync;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.TransactionResult;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * A player's session lease, {@code <domain>:session:<uuid>}: the token of the one session allowed to
 * save their data. Taken at pre-login, renewed by a per-shard heartbeat, checked by every save, and
 * released once the session's saves have committed. See ARCHITECTURE.md, "Session leases".
 */
final class SessionLease {
	static final long TTL_MILLIS = 90_000;
	/* Often enough that only a shard below ~2 TPS for the whole TTL loses its leases */
	static final long HEARTBEAT_TICKS = 10 * 20;
	static final long ACQUIRE_WAIT_MILLIS = 20_000;
	/* ACQUIRE_WAIT_MILLIS, but shorter in tests */
	private static volatile long mAcquireWaitMillis = ACQUIRE_WAIT_MILLIS;

	private static final SecureRandom RANDOM = new SecureRandom();

	/* How long the last writer of a player's data is remembered: longer than a shard can hang before it is restarted */
	static final long WRITER_TTL_MILLIS = 60L * 60 * 1000;

	/*
	 * KEYS[1]: the lease, KEYS[2]: the last writer. ARGV[1]: token, ARGV[2]: TTL ms, ARGV[3]: history
	 * length, ARGV[4]: writer TTL ms. Then ops: L key value (push and trim), S key value (replace list
	 * head), H key field value (hset). Writes only if the lease is this token's, or free and no other
	 * session has written since this one (then takes it). Extends the lease and records the writer.
	 */
	static final String FENCED_WRITE_SCRIPT = """
		local holder = redis.call('GET', KEYS[1])
		if holder ~= ARGV[1] then
			if holder then return redis.error_reply('LEASE_LOST held by ' .. holder) end
			local writer = redis.call('GET', KEYS[2])
			if writer and writer ~= ARGV[1] then return redis.error_reply('LEASE_LOST written since by ' .. writer) end
		end
		redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
		redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[4])
		local trim = tonumber(ARGV[3])
		local i = 5
		while i <= #ARGV do
			local op = ARGV[i]
			if op == 'L' then
				redis.call('LPUSH', ARGV[i + 1], ARGV[i + 2])
				redis.call('LTRIM', ARGV[i + 1], 0, trim)
				i = i + 3
			elseif op == 'S' then
				redis.call('LSET', ARGV[i + 1], 0, ARGV[i + 2])
				i = i + 3
			elseif op == 'H' then
				redis.call('HSET', ARGV[i + 1], ARGV[i + 2], ARGV[i + 3])
				i = i + 4
			else
				return redis.error_reply('unknown op ' .. tostring(op))
			end
		end
		return 1
		""";

	/*
	 * KEYS: n leases, then their n last-writer keys. ARGV[1]: TTL ms, ARGV[2]: how many of the first
	 * leases may be taken again if missing (and no other session has written since), then a token per
	 * lease. The rest are only extended. Returns the 1-based indexes of leases held or written by someone else.
	 */
	private static final String RENEW_SCRIPT = """
		local lost = {}
		local n = #KEYS / 2
		local retakable = tonumber(ARGV[2])
		for i = 1, n do
			local token = ARGV[i + 2]
			local holder = redis.call('GET', KEYS[i])
			if holder == token then
				redis.call('PEXPIRE', KEYS[i], ARGV[1])
			elseif holder then
				table.insert(lost, i)
			elseif i <= retakable then
				local writer = redis.call('GET', KEYS[n + i])
				if writer and writer ~= token then
					table.insert(lost, i)
				else
					redis.call('SET', KEYS[i], token, 'PX', ARGV[1])
				end
			end
		end
		return lost
		""";

	/* ARGV: a token per key. Deletes those still held by their token; returns how many */
	private static final String RELEASE_SCRIPT = """
		local released = 0
		for i = 1, #KEYS do
			if redis.call('GET', KEYS[i]) == ARGV[i] then
				redis.call('DEL', KEYS[i])
				released = released + 1
			end
		end
		return released
		""";

	/* Deletes the lease if its holder is a session of this shard. ARGV[1]: "<shard>:" */
	private static final String RELEASE_PREFIX_SCRIPT = """
		local holder = redis.call('GET', KEYS[1])
		if holder and string.sub(holder, 1, #ARGV[1]) == ARGV[1] then
			redis.call('DEL', KEYS[1])
			return 1
		end
		return 0
		""";

	private SessionLease() {
	}

	static String key(UUID uuid) {
		return CommonConfig.getServerDomain() + ":session:" + uuid;
	}

	static String writerKey(UUID uuid) {
		return CommonConfig.getServerDomain() + ":session:" + uuid + ":writer";
	}

	static String newToken() {
		byte[] bytes = new byte[8];
		RANDOM.nextBytes(bytes);
		return CommonConfig.getShardName() + ":" + HexFormat.of().formatHex(bytes);
	}

	static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	/** Takes the lease, waiting up to the acquire wait for its holder. Blocks. Returns null if taken, else the holder */
	static @Nullable String acquire(UUID uuid, String token) throws Exception {
		String key = key(uuid);
		long deadline = System.currentTimeMillis() + mAcquireWaitMillis;
		long sleep = 25;
		while (true) {
			TransactionResult result = RedisAPI.multiStringBytes(conn -> {
				conn.set(key, bytes(token), SetArgs.Builder.nx().px(TTL_MILLIS));
				conn.get(key);
			}).get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			byte[] current = result.get(1);
			String holder = current == null ? "(none)" : new String(current, StandardCharsets.UTF_8);
			if (token.equals(holder)) {
				return null;
			}
			if (System.currentTimeMillis() + sleep > deadline) {
				return holder;
			}
			Thread.sleep(sleep);
			sleep = Math.min(sleep * 2, 250);
		}
	}

	/**
	 * Renews these leases (key: player, value: token): {@code retakable} ones are taken again if missing,
	 * {@code extendOnly} ones (sessions that ended, logins not yet loaded) only extended. Completes with
	 * the players whose lease someone else holds, or whose data another session wrote since.
	 */
	static CompletableFuture<List<UUID>> renew(Map<UUID, String> retakable, Map<UUID, String> extendOnly) {
		if (retakable.isEmpty() && extendOnly.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		List<UUID> players = new ArrayList<>(retakable.size() + extendOnly.size());
		List<String> keys = new ArrayList<>();
		List<byte[]> args = new ArrayList<>();
		args.add(bytes(Long.toString(TTL_MILLIS)));
		args.add(bytes(Integer.toString(retakable.size())));
		for (Map<UUID, String> leases : List.of(retakable, extendOnly)) {
			for (Map.Entry<UUID, String> lease : leases.entrySet()) {
				players.add(lease.getKey());
				keys.add(key(lease.getKey()));
				args.add(bytes(lease.getValue()));
			}
		}
		for (UUID player : players) {
			keys.add(writerKey(player));
		}
		RedisFuture<List<Long>> result;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			result = conn.eval(RENEW_SCRIPT, ScriptOutputType.MULTI, keys.toArray(String[]::new), args.toArray(byte[][]::new));
		}
		return result.toCompletableFuture().thenApply(lost -> lost.stream().map(i -> players.get(i.intValue() - 1)).toList());
	}

	/** Releases these leases, each only if still held by its token */
	static CompletableFuture<Long> release(Map<UUID, String> leases) {
		if (leases.isEmpty()) {
			return CompletableFuture.completedFuture(0L);
		}
		String[] keys = leases.keySet().stream().map(SessionLease::key).toArray(String[]::new);
		byte[][] args = leases.values().stream().map(SessionLease::bytes).toArray(byte[][]::new);
		RedisFuture<Long> result;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			result = conn.eval(RELEASE_SCRIPT, ScriptOutputType.INTEGER, keys, args);
		}
		return result.toCompletableFuture();
	}

	static CompletableFuture<Long> release(UUID uuid, String token) {
		return release(Map.of(uuid, token));
	}

	/** Writes {@code ops} (see {@link FencedWrite}) if {@code token} holds the player's lease, atomically */
	static CompletableFuture<Long> fencedWrite(UUID uuid, String token, FencedWrite ops) {
		RedisFuture<Long> result;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			result = conn.eval(FENCED_WRITE_SCRIPT, ScriptOutputType.INTEGER, new String[] {key(uuid), writerKey(uuid)}, ops.args(token));
		}
		return result.toCompletableFuture();
	}

	/* For tests */
	static void setAcquireWaitMillis(long millis) {
		mAcquireWaitMillis = millis;
	}

	/** Deletes a lease held by any session of this shard: for players found online after a plugin reload */
	static CompletableFuture<Long> releaseIfHeldByThisShard(UUID uuid) {
		RedisFuture<Long> result;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			result = conn.eval(RELEASE_PREFIX_SCRIPT, ScriptOutputType.INTEGER, new String[] {key(uuid)}, bytes(CommonConfig.getShardName() + ":"));
		}
		return result.toCompletableFuture();
	}

	static boolean isLeaseLost(@Nullable Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause.getMessage() != null && cause.getMessage().contains("LEASE_LOST")) {
				return true;
			}
		}
		return false;
	}

	/** The holder of a player's lease and its remaining time, for commands */
	static CompletableFuture<String> describe(UUID uuid) {
		RedisFuture<byte[]> holder;
		RedisFuture<Long> ttl;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			holder = conn.get(key(uuid));
			ttl = conn.pttl(key(uuid));
		}
		return holder.toCompletableFuture().thenCombine(ttl.toCompletableFuture(), (h, t) ->
			h == null ? "none" : new String(h, StandardCharsets.UTF_8) + " (expires in " + t + "ms)");
	}

	/** Deletes a lease whoever holds it, for a moderator who knows it is stale */
	static CompletableFuture<Long> forceClear(UUID uuid) {
		RedisFuture<Long> result;
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			result = conn.del(key(uuid));
		}
		return result.toCompletableFuture();
	}

	/** The writes of one fenced save, built up as ops for {@link #FENCED_WRITE_SCRIPT} */
	static final class FencedWrite {
		private final List<byte[]> mOps = new ArrayList<>();

		FencedWrite push(String key, byte[] value) {
			mOps.add(bytes("L"));
			mOps.add(bytes(key));
			mOps.add(value);
			return this;
		}

		FencedWrite push(String key, String value) {
			return push(key, bytes(value));
		}

		FencedWrite replaceHead(String key, String value) {
			mOps.add(bytes("S"));
			mOps.add(bytes(key));
			mOps.add(bytes(value));
			return this;
		}

		FencedWrite hset(String key, String field, String value) {
			mOps.add(bytes("H"));
			mOps.add(bytes(key));
			mOps.add(bytes(field));
			mOps.add(bytes(value));
			return this;
		}

		private byte[][] args(String token) {
			List<byte[]> args = new ArrayList<>(mOps.size() + 4);
			args.add(bytes(token));
			args.add(bytes(Long.toString(TTL_MILLIS)));
			args.add(bytes(Integer.toString(BukkitConfigAPI.getHistoryAmount())));
			args.add(bytes(Long.toString(WRITER_TTL_MILLIS)));
			args.addAll(mOps);
			return args.toArray(byte[][]::new);
		}
	}
}
