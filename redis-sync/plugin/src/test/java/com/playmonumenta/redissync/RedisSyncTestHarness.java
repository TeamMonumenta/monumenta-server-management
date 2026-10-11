package com.playmonumenta.redissync;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.adapters.TestVersionAdapter;
import com.playmonumenta.redissync.utils.MMLog;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.io.File;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Everything a save/load test needs: a mock Bukkit server, a real redis, and a real
 * set of the plugin's player data listeners wired to both.
 *
 * <p>Test classes annotated {@code @ExtendWith(RedisSyncTestHarness.PerTest.class)} get a fresh
 * harness as a test method parameter, closed when the test ends.
 *
 * <p>This is the only file in the test sources that imports MockBukkit, so moving to a newer
 * MockBukkit (which renamed its packages) is a one-file change.
 *
 * <h2>Login and disconnect follow Paper, not MockBukkit</h2>
 * MockBukkit's {@code addPlayer} and {@code disconnect} fire events in an order no real server
 * does, and never save in between, which hides exactly the ordering bugs these tests are for.
 * {@link #login} and {@link #disconnect} instead replay Paper 1.20.4, as read from the server
 * jar's bytecode:
 * <ul>
 * <li>Login: AsyncPlayerPreLoginEvent on an async thread. Then on the main thread the ServerPlayer
 * is constructed (PlayerAdvancementDataLoadEvent), PlayerLoginEvent fires, and any player already
 * in the game with the same UUID is kicked ({@link #verifyLogin}). The configuration phase
 * follows, at least a tick during which the player has no game connection, so a kick does
 * nothing, as with CraftPlayer.kick. Then placeNewPlayer runs ({@link #placePlayer}):
 * PlayerDataLoadEvent, the player becomes visible to Bukkit, PlayerJoinEvent.</li>
 * <li>Disconnect ({@code Connection.handleDisconnection}): {@code PlayerList.remove} fires
 * PlayerQuitEvent, saves the player synchronously (PlayerDataSaveEvent, then
 * PlayerAdvancementDataSaveEvent), and removes them from the server. Only after that returns
 * does PlayerConnectionCloseEvent fire.</li>
 * <li>Kick: {@code PlayerList.remove} runs immediately, and the connection is torn down (firing
 * PlayerConnectionCloseEvent) on a later network tick.</li>
 * <li>A login refused at PlayerLoginEvent (bans, whitelist) is disconnected in the VERIFYING state.
 * Paper fires PlayerConnectionCloseEvent for login connections from VERIFYING onwards, but not
 * for ones refused earlier at AsyncPlayerPreLoginEvent.</li>
 * <li>Datapack reload ({@link #reloadDatapacks}) and shutdown with monumenta-mixins
 * ({@link #stopServer}) - see each.</li>
 * </ul>
 *
 * <h2>Driving a player</h2>
 * <ul>
 * <li>{@link #join} - a whole login that must succeed, then a tick so the join is over. What most
 * tests want.</li>
 * <li>{@link #login} - a whole login that may be refused; the join tick has not passed.</li>
 * <li>{@link #preLogin}, {@link #verifyLogin}, {@link #placePlayer} - the steps of a login, for
 * tests that interleave two of them ({@link #completeLogin} is the last two).</li>
 * <li>{@link #disconnect} - the client leaves; {@link TestPlayer#kick} - a plugin kicks;
 * {@link #closeUnplacedConnection} - a connection that never got into the game goes away;
 * {@link #disconnectWithCloseOffMainThread}.</li>
 * </ul>
 *
 * <h2>Waiting</h2>
 * <ul>
 * <li>{@link #awaitSaved} - ticks until the player's saves have committed.</li>
 * <li>{@link #awaitSavedWithoutTicking} - the same in real time, for tests that count ticks.</li>
 * <li>{@link #awaitPluginCommandsExecuted} - until redis has run everything the plugin has sent,
 * so a test can check that something was <em>not</em> written.</li>
 * <li>{@link #awaitWithTicks} - ticks until any condition holds.</li>
 * </ul>
 *
 * <h2>Progress</h2>
 * A player's data is stored in three places, written over two redis connections that are not
 * ordered against each other: playerdata (bytes), and scores and advancements (strings).
 * {@link #setProgress} sets one number in all three, and {@link #progress} reads it back, failing
 * if they disagree, so a test that checks progress after a login checks both connections.
 */
public final class RedisSyncTestHarness implements AutoCloseable {
	/** Long enough to cover a local redis round trip, short enough to fail fast. */
	private static final long AWAIT_TIMEOUT_MS = 15_000;
	private static final File UNUSED_PATH = new File("unused-in-tests");
	private static final String PROGRESS_OBJECTIVE = "Progress";
	/* Real time between the ticks of the harness's waits */
	private static final long TICK_MS = 20;

	private final TestRedisServer mRedis;
	private final ServerMock mServer;
	private final MockPlugin mPlugin;
	private final TestVersionAdapter mAdapter = new TestVersionAdapter();
	private final Objective mProgressObjective;
	/* The harness's own connection, so pausing redis or reading what it holds never queues behind the plugin */
	private final RedisClient mControlClient;
	private final StatefulRedisConnection<String, String> mControlConnection;

	public RedisSyncTestHarness() throws Exception {
		mRedis = TestRedisServer.get();
		mServer = MockBukkit.mock();
		mPlugin = MockBukkit.createMockPlugin("MonumentaRedisSync");

		/*
		 * Every harness gets its own key namespace, so tests' keys cannot collide even in a
		 * long-lived external redis, and nothing ever needs flushing.
		 */
		String serverDomain = "mrstest_" + UUID.randomUUID();

		MMLog.init("MonumentaRedisSyncTest");
		new BukkitConfigAPI(mPlugin.getLogger(), mRedis.getHost(), mRedis.getPort(), serverDomain,
			"test_shard", 20, 0, false, true, false, null);
		/*
		 * The plugin's redis connections are shared by every test until one shuts them down: each
		 * RedisAPI makes its own netty allocator, which is never given back, and a JVM runs out of
		 * room for them after a few dozen. Each test still has its own keys.
		 */
		@SuppressWarnings("NullAway") // Null once shut down, which is what is checked
		boolean connected = RedisAPI.getInstance() != null;
		if (!connected) {
			new RedisAPI(mRedis.getHost(), mRedis.getPort());
		}

		/* The same listeners MonumentaRedisSync.onEnable registers, bar autosave which tests drive by hand */
		MonumentaRedisSync.registerPlayerDataListeners(mPlugin, mAdapter);

		mProgressObjective = mServer.getScoreboardManager().getMainScoreboard()
			.registerNewObjective(PROGRESS_OBJECTIVE, Criteria.DUMMY, Component.text(PROGRESS_OBJECTIVE));

		mControlClient = RedisClient.create(RedisURI.Builder.redis(mRedis.getHost(), mRedis.getPort()).build());
		mControlConnection = mControlClient.connect();
	}

	/** Gives each test method that takes a {@link RedisSyncTestHarness} a fresh one, closed after the test. */
	public static final class PerTest implements ParameterResolver {
		@Override
		public boolean supportsParameter(ParameterContext parameter, ExtensionContext extension) {
			return parameter.getParameter().getType() == RedisSyncTestHarness.class;
		}

		@Override
		public Object resolveParameter(ParameterContext parameter, ExtensionContext extension) {
			return extension.getStore(ExtensionContext.Namespace.create(PerTest.class))
				.getOrComputeIfAbsent("harness", key -> {
					try {
						return new Closer(new RedisSyncTestHarness());
					} catch (Exception ex) {
						throw new IllegalStateException("Failed to start the test harness", ex);
					}
				}, Closer.class).mHarness;
		}

		private record Closer(RedisSyncTestHarness mHarness) implements ExtensionContext.Store.CloseableResource {
			@Override
			public void close() throws Exception {
				mHarness.close();
			}
		}
	}

	public TestVersionAdapter getAdapter() {
		return mAdapter;
	}

	public void tick() {
		mServer.getScheduler().performOneTick();
	}

	public void tick(int ticks) {
		mServer.getScheduler().performTicks(ticks);
	}

	/** Runs {@code task} on the main thread {@code ticks} ticks from now */
	public void runLater(int ticks, Runnable task) {
		mServer.getScheduler().runTaskLater(mPlugin, task, ticks);
	}

	public void callEvent(Event event) {
		mServer.getPluginManager().callEvent(event);
	}

	/** Runs {@code handler} for every {@code type} event, as another plugin's listener would. */
	public <T extends Event> void onEvent(Class<T> type, EventPriority priority, Consumer<T> handler) {
		mServer.getPluginManager().registerEvent(type, new Listener() { }, priority,
			(listener, event) -> {
				if (type.isInstance(event)) {
					handler.accept(type.cast(event));
				}
			}, mPlugin, false);
	}

	public <T extends Event> void onEvent(Class<T> type, Consumer<T> handler) {
		onEvent(type, EventPriority.NORMAL, handler);
	}

	/** Counts {@code type} events from now on. */
	public AtomicInteger countEvents(Class<? extends Event> type) {
		AtomicInteger count = new AtomicInteger();
		onEvent(type, event -> count.incrementAndGet());
		return count;
	}

	/* ******************* Players ******************* */

	/**
	 * Sets the player's progress to {@code value} in their live state, everywhere it is stored:
	 * playerdata (XpLevel), a score, and advancements. The next save writes it.
	 */
	public void setProgress(Player player, int value) {
		mAdapter.getLiveData(player).addProperty("XpLevel", value);
		mProgressObjective.getScore(player.getName()).setScore(value);
		mAdapter.setLiveAdvancements(player, advancementsFor(value));
	}

	/**
	 * The player's progress in their live state (after a login, what was loaded), or -1 if nothing
	 * was. Fails if playerdata, score and advancements disagree.
	 */
	public int progress(Player player) {
		JsonObject live = mAdapter.getLiveData(player);
		int data = live.has("XpLevel") ? live.get("XpLevel").getAsInt() : -1;
		var score = mProgressObjective.getScore(player.getName());
		int scored = score.isScoreSet() ? score.getScore() : -1;
		String advancements = mAdapter.getLiveAdvancements(player);
		int advanced = advancements == null || advancements.equals("{}") ? -1 : progressOfAdvancements(advancements);
		if (data != scored || data != advanced) {
			throw new AssertionError(player.getName() + "'s progress is torn: playerdata=" + data
				+ " score=" + scored + " advancements=" + advanced);
		}
		return data;
	}

	/** Whether the local scoreboard holds any of the player's scores */
	public boolean hasScores(Player player) {
		return mProgressObjective.getScore(player.getName()).isScoreSet();
	}

	/**
	 * The progress of each save in redis, newest first, as committed. Fails unless the playerdata,
	 * scores and advancements history lists hold the same number of entries, and each entry pairs
	 * with the same save on all three, which rollback, stash and loadFromPlayer rely on, as they
	 * read every list at one index.
	 */
	public List<Integer> savedProgressHistory(Player player) throws Exception {
		String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(player);
		String scoresPath = MonumentaRedisSyncAPI.getRedisScoresPath(player);
		String advancementsPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(player);
		RedisAsyncCommands<String, String> redis = mControlConnection.async();
		List<String> data = redis.lrange(dataPath, 0, -1).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		List<String> scores = redis.lrange(scoresPath, 0, -1).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		List<String> advancements = redis.lrange(advancementsPath, 0, -1).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		if (data.size() != scores.size() || data.size() != advancements.size()) {
			throw new AssertionError("History lists differ in length: playerdata=" + data.size()
				+ " scores=" + scores.size() + " advancements=" + advancements.size());
		}
		List<Integer> history = new ArrayList<>();
		for (int i = 0; i < data.size(); i++) {
			JsonObject nbt = JsonParser.parseString(data.get(i)).getAsJsonObject();
			int value = nbt.has("XpLevel") ? nbt.get("XpLevel").getAsInt() : -1;
			JsonObject score = JsonParser.parseString(scores.get(i)).getAsJsonObject();
			int scored = score.has(PROGRESS_OBJECTIVE) ? score.get(PROGRESS_OBJECTIVE).getAsInt() : -1;
			int advanced = progressOfAdvancements(advancements.get(i));
			if (value != scored || value != advanced) {
				throw new AssertionError("History entry " + i + " is torn: playerdata=" + value
					+ " score=" + scored + " advancements=" + advanced);
			}
			history.add(value);
		}
		return history;
	}

	/** The progress of the newest committed save, or -1 if there is none. See {@link #savedProgressHistory}. */
	public int savedProgress(Player player) throws Exception {
		List<Integer> history = savedProgressHistory(player);
		return history.isEmpty() ? -1 : history.get(0);
	}

	private static String advancementsFor(int value) {
		return "{\"progress\":" + value + "}";
	}

	private static int progressOfAdvancements(String advancements) {
		JsonObject json = JsonParser.parseString(advancements).getAsJsonObject();
		return json.has("progress") ? json.get("progress").getAsInt() : -1;
	}

	/**
	 * A player connection. Kicking it disconnects the way Paper does rather than the way MockBukkit
	 * does. Also implements the shoulder entity getters that transfers read and MockBukkit lacks.
	 */
	@SuppressWarnings("unchecked") // PlayerMock's ban() overrides, inherited as-is
	public final class TestPlayer extends PlayerMock {
		/*
		 * Whether this connection has a game connection, i.e. placeNewPlayer ran and it has not been
		 * removed. Per object, unlike isOnline(), which (as on Paper) only asks whether anyone with
		 * this UUID is online.
		 */
		private boolean mPlaced = false;
		/* Plugin messages sent to this player's client; for a transfer, what the proxy would act on */
		private final List<String> mPluginMessages = new CopyOnWriteArrayList<>();
		private final List<String> mChat = new CopyOnWriteArrayList<>();
		private volatile boolean mFailPluginMessages = false;
		/* What the advancement load handed the server, applied once the player is placed */
		private @Nullable String mLoadedAdvancements = null;

		private TestPlayer(String name, UUID uuid) {
			super(mServer, name, uuid);
		}

		@Override
		public void kick(Component message) {
			kick(message, PlayerKickEvent.Cause.PLUGIN);
		}

		@Override
		public void kick() {
			kick(Component.empty());
		}

		@Override
		public void kick(Component message, PlayerKickEvent.Cause cause) {
			/* CraftPlayer.kick does nothing without a game connection */
			if (!mPlaced) {
				return;
			}
			PlayerKickEvent event = new PlayerKickEvent(this, Component.empty(), message, cause);
			callEvent(event);
			if (event.isCancelled()) {
				return;
			}
			disconnectFromWorld();
			/* The connection itself is torn down on a later network tick */
			mServer.getScheduler().runTask(mPlugin, this::closeConnection);
		}

		@Override
		public void sendPluginMessage(Plugin source, String channel, byte[] message) {
			if (mFailPluginMessages) {
				throw new IllegalStateException("test: plugin message channel broken");
			}
			mPluginMessages.add(channel + ":" + new String(message, StandardCharsets.UTF_8));
		}

		/** Makes sending plugin messages to this player throw, as a closed channel would */
		public void failPluginMessages() {
			mFailPluginMessages = true;
		}

		/** Whether this connection is in the game: placed, and not yet removed */
		public boolean isPlaced() {
			return mPlaced;
		}

		public List<String> getPluginMessages() {
			return mPluginMessages;
		}

		/*
		 * Chat sent to this player, as plain text. The plugin sends some from redis callbacks, which
		 * MockBukkit's own message queue is not safe for.
		 */
		@Override
		@SuppressWarnings({"deprecation", "UnstableApiUsage"}) // The method every Adventure sendMessage ends up in
		public void sendMessage(Identity source, Component message, MessageType type) {
			mChat.add(PlainTextComponentSerializer.plainText().serialize(message));
		}

		public boolean wasTold(String text) {
			return mChat.stream().anyMatch(line -> line.contains(text));
		}

		public List<String> getChat() {
			return mChat;
		}

		@Override
		@SuppressWarnings("deprecation")
		public @Nullable Entity getShoulderEntityLeft() {
			return null;
		}

		@Override
		@SuppressWarnings("deprecation")
		public @Nullable Entity getShoulderEntityRight() {
			return null;
		}

		/* PlayerList.remove: quit event, the synchronous final save, then removal from the server */
		private void disconnectFromWorld() {
			callEvent(new PlayerQuitEvent(this, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
			mAdapter.savePlayer(this);
			mServer.getPlayerList().disconnectPlayer(this);
			mPlaced = false;
		}

		private void closeConnection() {
			callEvent(new PlayerConnectionCloseEvent(getUniqueId(), getName(), InetAddress.getLoopbackAddress(), false));
		}
	}

	/**
	 * A player who logged in, made {@code progress}, and left, with that save committed. Returns
	 * the connection they left on.
	 */
	public TestPlayer savedAndLeft(String name, int progress) throws Exception {
		TestPlayer player = join(name);
		setProgress(player, progress);
		disconnect(player);
		awaitSaved(player);
		return player;
	}

	/** A new connection for the account named {@code name}, not yet logged in; the same name always gives the same UUID. */
	public TestPlayer newPlayer(String name) {
		return new TestPlayer(name, UUID.nameUUIDFromBytes(("test:" + name).getBytes(StandardCharsets.UTF_8)));
	}

	/**
	 * Logs a player in following Paper's event order (see the class comment).
	 *
	 * <p>Data handed back by the load events becomes the player's live state, as it would on a real
	 * server, so a later save writes back exactly what was loaded plus whatever the test changed.
	 *
	 * @return ALLOWED if the player joined; otherwise the pre-login result, or KICK_OTHER if
	 * PlayerLoginEvent refused them
	 */
	public AsyncPlayerPreLoginEvent.Result login(TestPlayer player) throws InterruptedException {
		AsyncPlayerPreLoginEvent.Result result = preLogin(player);
		if (result != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return result;
		}
		return completeLogin(player);
	}

	/**
	 * A new connection for {@code name} logs in, which must succeed, and then a tick passes so the
	 * join is over and the player saves normally.
	 */
	public TestPlayer join(String name) throws InterruptedException {
		TestPlayer player = newPlayer(name);
		AsyncPlayerPreLoginEvent.Result result = login(player);
		if (result != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			throw new AssertionError("Login for " + name + " was refused: " + result);
		}
		tick();
		return player;
	}

	/** The async half of {@link #login}, for tests that interleave two logins. */
	public AsyncPlayerPreLoginEvent.Result preLogin(TestPlayer player) throws InterruptedException {
		AsyncPlayerPreLoginEvent preLogin = new AsyncPlayerPreLoginEvent(
			player.getName(), InetAddress.getLoopbackAddress(), player.getUniqueId(), false);
		Thread worker = new Thread(() -> callEvent(preLogin), "mrs-test-prelogin");
		worker.start();
		/* The test thread stands in for the main thread, which keeps ticking while a login waits */
		awaitWithTicks("pre-login for " + player.getName(), () -> !worker.isAlive());
		return preLogin.getLoginResult();
	}

	/** The main thread half of {@link #login}, after pre-login allowed the player. */
	public AsyncPlayerPreLoginEvent.Result completeLogin(TestPlayer player) {
		if (!verifyLogin(player)) {
			return AsyncPlayerPreLoginEvent.Result.KICK_OTHER;
		}
		/* The configuration phase takes at least a tick; the player has no game connection during it */
		tick();
		placePlayer(player);
		return AsyncPlayerPreLoginEvent.Result.ALLOWED;
	}

	/**
	 * The login's VERIFYING step: the ServerPlayer is constructed (advancement load), PlayerLoginEvent
	 * fires, and then, as {@code PlayerList.disconnectAllPlayersWithProfile} does, any player
	 * already online with this UUID is kicked. Afterwards the connection is in the configuration
	 * phase, which {@link #placePlayer} ends.
	 *
	 * @return whether the login was allowed; if not, the connection has already closed
	 */
	@SuppressWarnings("ReferenceEquality") // Connections are distinct objects for the same UUID
	public boolean verifyLogin(TestPlayer player) {
		PlayerAdvancementDataLoadEvent advancementLoad = new PlayerAdvancementDataLoadEvent(player, UNUSED_PATH);
		callEvent(advancementLoad);
		player.mLoadedAdvancements = advancementLoad.getJsonData();

		PlayerLoginEvent loginEvent = new PlayerLoginEvent(player, "localhost", InetAddress.getLoopbackAddress());
		callEvent(loginEvent);
		if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
			/*
			 * Disconnected while VERIFYING, one of the login states that does fire the close event.
			 * As with any disconnect, the connection is torn down on a later network tick
			 */
			mServer.getScheduler().runTask(mPlugin, player::closeConnection);
			return false;
		}

		Player existing = mServer.getPlayer(player.getUniqueId());
		if (existing instanceof TestPlayer other && other != player) {
			other.kick(Component.translatable("multiplayer.disconnect.duplicate_login"), PlayerKickEvent.Cause.DUPLICATE_LOGIN);
		}
		return true;
	}

	/** Ends the configuration phase: placeNewPlayer loads the playerdata, adds the player, and fires the join. */
	public void placePlayer(TestPlayer player) {
		PlayerDataLoadEvent dataLoad = new PlayerDataLoadEvent(player, UNUSED_PATH);
		callEvent(dataLoad);
		Object loaded = dataLoad.getData();
		String advancements = player.mLoadedAdvancements;
		mAdapter.setLiveData(player, loaded == null ? new JsonObject() : ((JsonObject) loaded).deepCopy(),
			advancements == null ? "{}" : advancements);

		mServer.getPlayerList().addPlayer(player);
		player.mPlaced = true;
		callEvent(new PlayerJoinEvent(player, Component.empty()));
	}

	/** The client closes the connection: all of Connection.handleDisconnection at once. */
	public void disconnect(TestPlayer player) {
		player.disconnectFromWorld();
		player.closeConnection();
	}

	/**
	 * The client goes away before it was ever placed, during the configuration phase say. Nothing
	 * is removed or saved, since nothing was added; only the close event fires.
	 */
	public void closeUnplacedConnection(TestPlayer player) {
		if (player.mPlaced) {
			throw new IllegalStateException(player.getName() + " is placed; use disconnect");
		}
		player.closeConnection();
	}

	/**
	 * As {@link #disconnect}, but the close event is fired on another thread, which Paper does not
	 * rule out. Returns once that thread's listeners have run.
	 */
	public void disconnectWithCloseOffMainThread(TestPlayer player) throws InterruptedException {
		player.disconnectFromWorld();
		Thread closer = new Thread(player::closeConnection, "mrs-test-netty");
		closer.start();
		closer.join(AWAIT_TIMEOUT_MS);
		if (closer.isAlive()) {
			throw new AssertionError("Close event listeners did not return");
		}
	}

	/* ******************* Waiting ******************* */

	/**
	 * Ticks until {@code condition} holds, or fails.
	 *
	 * <p>MockBukkit runs runTaskAsynchronously immediately on a real pool but requires explicit
	 * ticks for sync and delayed tasks. Ticks are {@value #TICK_MS}ms of real time apart: faster
	 * than a real server, but not so fast that a timeout measured in ticks (a transfer lock's, say)
	 * runs out while redis is merely slow.
	 */
	public void awaitWithTicks(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			tick();
			Thread.sleep(TICK_MS);
		}
		throw new AssertionError("Timed out after " + AWAIT_TIMEOUT_MS + "ms waiting for: " + what);
	}

	/** As {@link #awaitWithTicks}, but fails after {@code maxTicks} ticks rather than after a time. */
	public void awaitWithTicks(String what, BooleanSupplier condition, int maxTicks) throws InterruptedException {
		for (int ticks = 0; ticks <= maxTicks; ticks++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(TICK_MS);
			tick();
		}
		throw new AssertionError("Not done after " + maxTicks + " ticks: " + what);
	}

	/** Ticks until every save the plugin has in flight for this player has committed, through the plugin's own API. */
	public void awaitSaved(Player player) throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		PlayerSessions.waitForSaves(player, done::countDown, false);
		awaitWithTicks("pending saves to commit for " + player.getName(), () -> done.getCount() == 0);
	}

	/**
	 * Waits in real time, without advancing the scheduler, until redis has acknowledged every save
	 * in flight for this player. For tests where how many ticks pass is part of what is asserted.
	 */
	public void awaitSavedWithoutTicking(Player player) throws InterruptedException {
		long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
		while (PlayerSessions.hasUnsettledSaves(player)) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("Timed out waiting for saves to commit for " + player.getName());
			}
			Thread.sleep(5);
		}
		/*
		 * Whatever waits on the save (scheduling the next step onto the main thread) runs on the
		 * redis thread as the save completes, and is not ordered against the bookkeeping checked
		 * above. It takes microseconds; this margin keeps a test's next tick from overtaking it.
		 */
		Thread.sleep(20);
	}

	/**
	 * Waits until redis has executed every command the plugin has sent so far. Each of the plugin's
	 * connections is FIFO, so a round trip on both is a barrier: after it, a save that was never
	 * made cannot still be on its way.
	 */
	public void awaitPluginCommandsExecuted() throws Exception {
		RedisFuture<String> strings;
		RedisFuture<String> bytes;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			strings = conn.ping();
		}
		try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
			bytes = conn.ping();
		}
		strings.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		bytes.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	/* ******************* Redis ******************* */

	/**
	 * Holds every write from every client for {@code millis}, as a slow or busy redis would.
	 *
	 * <p>Reads from other connections carry on, but a paused client's later commands queue behind
	 * its paused write, so this is exactly the window between a save being handed to redis and it
	 * committing that production code has to defend against.
	 */
	public void pauseRedisWrites(long millis) throws Exception {
		/* lettuce's clientPause() has no WRITE mode */
		mControlConnection.async().dispatch(CommandType.CLIENT, new StatusOutput<>(StringCodec.UTF8),
				new CommandArgs<>(StringCodec.UTF8).add("PAUSE").add(millis).add("WRITE"))
			.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	/*
	 * Reads and writes below go over the harness's own connection, so they never queue behind the
	 * plugin's commands: a read sees exactly what redis has committed.
	 */

	public @Nullable String redisListEntry(String path, int index) throws Exception {
		return mControlConnection.async().lindex(path, index).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	public long redisListLength(String path) throws Exception {
		return mControlConnection.async().llen(path).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	public @Nullable String redisHashGet(String path, String field) throws Exception {
		return mControlConnection.async().hget(path, field).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	public void redisSetListEntry(String path, int index, String value) throws Exception {
		mControlConnection.async().lset(path, index, value).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	/** Replaces a key with a plain string, so any list command on it fails with WRONGTYPE. */
	public void redisBreakKey(String path) throws Exception {
		mControlConnection.async().set(path, "not a list").get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	public void redisRename(String from, String to) throws Exception {
		mControlConnection.async().rename(from, to).get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	/* ******************* Server events ******************* */

	/**
	 * Replaces the plugin's listeners with fresh ones, as a plugin reload (PlugMan, /reload) would,
	 * with any players still online.
	 */
	public void reloadPlugin() {
		HandlerList.unregisterAll(mPlugin);
		MonumentaRedisSync.registerPlayerDataListeners(mPlugin, mAdapter);
	}

	/**
	 * Disables the plugin as Paper does: it is marked disabled, MonumentaRedisSync.onDisable runs
	 * (ending with redis closed), and then its scheduled tasks are cancelled. Without
	 * monumenta-mixins, Paper does this before it saves or removes the players still online, so
	 * this is also the last the plugin hears of them.
	 */
	public void disablePlugin() {
		mServer.getPluginManager().disablePlugin(mPlugin);
		PlayerSessions.onDisable();
		RedisAPI.getInstance().shutdown();
		mServer.getScheduler().cancelTasks(mPlugin);
	}

	/**
	 * A datapack reload, as {@code MinecraftServer.reloadResources} does it: each online player's
	 * advancements are saved on their own, then reloaded (re-firing the advancement load), and
	 * only then does ServerResourcesReloadedEvent fire.
	 */
	public void reloadDatapacks() {
		List<TestPlayer> online = placedPlayers();
		for (TestPlayer player : online) {
			mAdapter.saveAdvancements(player);
		}
		for (TestPlayer player : online) {
			PlayerAdvancementDataLoadEvent reload = new PlayerAdvancementDataLoadEvent(player, UNUSED_PATH);
			callEvent(reload);
			/* With nothing handed back, the server loads the player's advancements file, of which there are none with this plugin */
			String json = reload.getJsonData();
			mAdapter.setLiveAdvancements(player, json == null ? "{}" : json);
		}
		callEvent(new ServerResourcesReloadedEvent(ServerResourcesReloadedEvent.Cause.COMMAND));
	}

	/**
	 * Stops the server the way it does with monumenta-mixins (MinecraftServerMixin.savePlayers):
	 * every online player is saved, then removed (a disconnect that saves them again), then 100ms
	 * pass and plugins are disabled. The server's network has stopped by then, so no connection
	 * close event ever arrives for those players.
	 */
	public void stopServer() throws InterruptedException {
		List<TestPlayer> online = placedPlayers();
		for (TestPlayer player : online) {
			mAdapter.savePlayer(player);
		}
		for (TestPlayer player : online) {
			player.disconnectFromWorld();
		}
		Thread.sleep(100);
		disablePlugin();
	}

	private List<TestPlayer> placedPlayers() {
		List<TestPlayer> placed = new ArrayList<>();
		for (Player player : mServer.getOnlinePlayers()) {
			if (player instanceof TestPlayer testPlayer && testPlayer.mPlaced) {
				placed.add(testPlayer);
			}
		}
		return placed;
	}

	/** Fails if the plugin used the server off the main thread at any point, which listeners' exceptions would hide */
	@Override
	public void close() throws Exception {
		try {
			/* The redis is shared with the next test, which must not start with writes still paused */
			mControlConnection.async().dispatch(CommandType.CLIENT, new StatusOutput<>(StringCodec.UTF8),
					new CommandArgs<>(StringCodec.UTF8).add("UNPAUSE"))
				.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			mControlConnection.close();
			mControlClient.shutdown();
		} finally {
			MockBukkit.unmock();
		}
		if (!mAdapter.getThreadViolations().isEmpty()) {
			throw new AssertionError("The plugin used the server off the main thread: " + mAdapter.getThreadViolations());
		}
	}
}
