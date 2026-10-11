package com.playmonumenta.redissync.adapters;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Plain-Java stand-in for the NMS version adapter, so tests need no server internals.
 *
 * <p>"NBT" here is a {@link JsonObject} serialized as UTF-8 JSON. That is enough to cover
 * everything {@code DataEventListener} itself reads or writes, because the listener only ever
 * touches the world and position keys and otherwise treats the payload as opaque.
 *
 * <p><b>This class is not a specification of the real save format.</b>
 * {@code VersionAdapter_v1_20_R3} additionally splits out SpawnX/Y/Z, SpawnForced, SpawnAngle,
 * SpawnDimension, abilities.flying, FallFlying, FallDistance, OnGround, Dimension, Motion,
 * Paper.Origin and enteredNetherPosition. Nothing here covers those, or NBT encoding, or the
 * dataconverter upgrade path.
 *
 * <p>Lives in this package because {@link VersionAdapter.SaveData}'s constructor is protected.
 */
public class TestVersionAdapter implements VersionAdapter {
	/** Keys the real adapter moves out of the player payload and into per-world shard data. */
	private static final String[] SHARD_DATA_KEYS = {
		"Pos", "Rotation", "world", "WorldUUIDMost", "WorldUUIDLeast",
	};

	private final Gson mGson = new Gson();
	private final List<String> mThreadViolations = new CopyOnWriteArrayList<>();

	/*
	 * Stands in for the server's in-memory player state that a real save would read from. Keyed by
	 * the Player object, as each connection has its own ServerPlayer: two connections for one
	 * account hold separate state.
	 */
	private final Map<Player, JsonObject> mLivePlayerData = Collections.synchronizedMap(new IdentityHashMap<>());
	private final Map<Player, String> mLiveAdvancements = Collections.synchronizedMap(new IdentityHashMap<>());

	/** Sets the data that the next {@link #savePlayer} for this player will write. */
	public void setLiveData(Player player, JsonObject nbt, String advancementsJson) {
		mLivePlayerData.put(player, nbt);
		mLiveAdvancements.put(player, advancementsJson);
	}

	/** The player's live advancements JSON, or null if none were set. */
	public @Nullable String getLiveAdvancements(Player player) {
		return mLiveAdvancements.get(player);
	}

	public void setLiveAdvancements(Player player, String advancementsJson) {
		mLiveAdvancements.put(player, advancementsJson);
	}

	/** The player's live "NBT", for tests to change between saves. Created empty if nothing was set. */
	public JsonObject getLiveData(Player player) {
		return mLivePlayerData.computeIfAbsent(player, k -> new JsonObject());
	}

	@Override
	public JsonObject getPlayerScoresAsJson(String playerName, Scoreboard scoreboard) {
		requireMainThread();
		JsonObject out = new JsonObject();
		for (Objective objective : scoreboard.getObjectives()) {
			Score score = objective.getScore(playerName);
			if (score.isScoreSet()) {
				out.addProperty(objective.getName(), score.getScore());
			}
		}
		return out;
	}

	/**
	 * Paper removes the scores; MockBukkit's resetScores only sets them to 0 and leaves them
	 * "set", so they would be saved and asserted on as real zeroes. Unset them as Paper does.
	 * Reflection, rather than an import, keeps MockBukkit confined to the harness.
	 */
	@Override
	public void resetPlayerScores(String playerName, Scoreboard scoreboard) {
		requireMainThread();
		scoreboard.resetScores(playerName);
		for (Objective objective : scoreboard.getObjectives()) {
			Score score = objective.getScore(playerName);
			try {
				Field set = score.getClass().getDeclaredField("set");
				set.setAccessible(true);
				set.setBoolean(score, false);
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("MockBukkit's ScoreMock changed; update this workaround", ex);
			}
		}
	}

	@Override
	public Object retrieveSaveData(byte[] data, JsonObject shardData) {
		JsonObject nbt = mGson.fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
		for (String key : shardData.keySet()) {
			nbt.add(key, shardData.get(key));
		}
		return nbt;
	}

	@Override
	public SaveData extractSaveData(Object nbtObj, @Nullable ReturnParams returnParams) {
		/* Mutates in place, as the real adapter's copyX helpers do */
		JsonObject nbt = (JsonObject) nbtObj;
		JsonObject shardData = new JsonObject();
		for (String key : SHARD_DATA_KEYS) {
			if (nbt.has(key)) {
				shardData.add(key, nbt.get(key));
				nbt.remove(key);
			}
		}
		/* As the real adapter does, a transfer's return location replaces where the player is */
		if (returnParams != null && returnParams.mReturnLoc != null) {
			JsonArray pos = new JsonArray();
			pos.add(returnParams.mReturnLoc.getX());
			pos.add(returnParams.mReturnLoc.getY());
			pos.add(returnParams.mReturnLoc.getZ());
			shardData.add("Pos", pos);
		}
		return new SaveData(mGson.toJson(nbt).getBytes(StandardCharsets.UTF_8), mGson.toJson(shardData));
	}

	/**
	 * Fires the two save events the same way a real server save does, so tests exercise the
	 * production entry point ({@code MonumentaRedisSyncAPI.savePlayer}) rather than hand-firing.
	 */
	@Override
	public void savePlayer(Player player) {
		/* A real server always has some state to save, even for a player nothing was loaded for */
		JsonObject nbt = mLivePlayerData.computeIfAbsent(player, k -> new JsonObject());
		File path = new File("unused-in-tests");
		Bukkit.getPluginManager().callEvent(new PlayerDataSaveEvent(player, path, nbt.deepCopy()));
		String advancements = mLiveAdvancements.get(player);
		if (advancements != null) {
			Bukkit.getPluginManager().callEvent(new PlayerAdvancementDataSaveEvent(player, path, advancements));
		}
	}

	/** Saves only the advancements, as PlayerAdvancements.save does outside a full player save */
	public void saveAdvancements(Player player) {
		String advancements = mLiveAdvancements.get(player);
		if (advancements != null) {
			Bukkit.getPluginManager().callEvent(new PlayerAdvancementDataSaveEvent(player, new File("unused-in-tests"), advancements));
		}
	}

	/*
	 * The server's scoreboard is not thread safe, so the real adapter's callers must be on the main
	 * thread. Recorded as well as thrown, since an event listener's exception is only logged.
	 */
	private void requireMainThread() {
		if (!Bukkit.isPrimaryThread()) {
			String violation = "Scoreboard accessed off the main thread: " + Thread.currentThread().getName();
			mThreadViolations.add(violation);
			throw new IllegalStateException(violation);
		}
	}

	/** Every off-main-thread call made to this adapter, which the harness fails the test on */
	public List<String> getThreadViolations() {
		return mThreadViolations;
	}

	@Override
	public Object upgradePlayerData(Object nbtTagCompound) {
		return nbtTagCompound;
	}

	@Override
	public String upgradePlayerAdvancements(String advancementsStr) {
		return advancementsStr;
	}
}
