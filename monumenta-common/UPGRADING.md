# Upgrading to monumenta-common

---

## Part 1 -- Background

### What changed and why

Each Monumenta plugin previously maintained its own `CustomLogger` — a `java.util.logging.Logger` subclass with a manually-managed shadow level field that re-routed `FINE`/`FINER`/`FINEST` messages to `logger.info()` to bypass JUL's INFO threshold. Each plugin also had its own `ChangeLogLevel` command and a static `MMLog` facade over it.

Problems:
- Debug messages appeared at INFO level with no indication they were debug output.
- Level changes via `ChangeLogLevel` only updated the shadow variable inside `CustomLogger`; the actual underlying JUL handler was never changed.
- Level tracking logic was duplicated across every plugin.

The new approach is a shared `com.playmonumenta.common.MMLog` class backed by **log4j2** (`LogManager.getLogger(pluginId)`). Both Paper and Velocity use log4j2 as their logging backend (JUL calls from Bukkit are bridged into it). Using log4j2 directly means:

- `Configurator.setLevel()` actually works — it changes the logger's effective level in log4j2's hierarchy.
- Debug messages appear at `DEBUG` or `TRACE` level, not as fake `INFO`.
- No manual level tracking, no re-leveling hack.

`monumenta-common` ships as a single jar that loads as a plugin on both **Paper** and **Velocity**. Other plugins declare it as a hard dependency and construct their own `MMLog` instance. The `changeloglevel` command is registered per-plugin; the platform-specific registration path (CommandAPI on Paper, BrigadierCommand on Velocity) is handled by `MMLogPaper` and `MMLogVelocity` respectively — separate helper classes that are only loaded on their respective platforms.

### Level mapping (JUL -> log4j2)

| Old method   | New method   | log4j2 level |
|--------------|--------------|--------------|
| `finest()`   | `trace()`    | TRACE        |
| `finer()`    | `trace()`    | TRACE        |
| `fine()`     | `debug()`    | DEBUG        |
| `info()`     | `info()`     | INFO         |
| `warning()`  | `warning()`  | WARN         |
| `severe()`   | `severe()`   | ERROR        |

`finest()`, `finer()`, and `fine()` are kept as `@Deprecated` aliases so existing call sites compile without immediate changes.

### changeloglevel command

On Paper, each plugin registers `/changeloglevel <label> TRACE|DEBUG|INFO|WARN|ERROR`.
On Velocity, the command is `/changeloglevelvelocity <label> TRACE|DEBUG|INFO|WARN|ERROR` to avoid conflicts.

The label is derived automatically from the `MMLog` instance (`MMLog.getName()` returns the `pluginId` passed to its constructor), so `registerCommand` does not take a separate label argument. Use the plugin's display name as returned by `JavaPlugin.getName()` on Paper (e.g. `MonumentaNetworkRelay`, `MonumentaCommon`) — this keeps the command argument, permission node, and any `log4j2.xml` `<Logger name="...">` entries all consistent.
Permission is `<label>.changeloglevel` (all lowercase), e.g. `/changeloglevel MonumentaNetworkRelay INFO` requires `monumentanetworkrelay.changeloglevel`.

**Admin note:** old subcommand argument names have changed:

| Old      | New     |
|----------|---------|
| `FINE`   | `DEBUG` |
| `FINER`  | `TRACE` |
| `FINEST` | `TRACE` |
| `INFO`   | `INFO`  |

Update any server scripts or admin tooling that used the old names.

### Key files

- `monumenta-server-management/monumenta-common/src/main/java/com/playmonumenta/common/MMLog.java` — core log class (log4j2 only, no platform imports)
- `monumenta-server-management/monumenta-common/src/main/java/com/playmonumenta/common/MMLogPaper.java` — Paper command registration helper (CommandAPI)
- `monumenta-server-management/monumenta-common/src/main/java/com/playmonumenta/common/MMLogVelocity.java` — Velocity command registration helper (Velocity API)
- `monumenta-server-management/monumenta-common/src/main/java/com/playmonumenta/common/MonumentaCommonPlugin.java` — Paper entry point
- `monumenta-server-management/monumenta-common/src/main/java/com/playmonumenta/common/MonumentaCommonVelocityPlugin.java` — Velocity entry point

### Why command registration is split into separate classes

`MMLog` is loaded on both Paper and Velocity. HotSpot's bytecode verifier eagerly loads all classes referenced in method bodies when a class is first loaded. If `MMLog` contained `registerPaperCommand()` (CommandAPI) and `registerVelocityCommand()` (Velocity API) in the same class file, loading it on either platform would fail with `NoClassDefFoundError` for the other platform's classes.

`MMLogPaper` and `MMLogVelocity` are separate class files. Each is only loaded when explicitly referenced, which only happens in platform-specific code paths.

---

## Part 2 -- Upgrading a plugin

### Step 1 — Gradle setup

**`gradle/libs.versions.toml`** — add the version and library entry. Use the current SNAPSHOT commit hash from the local Maven repo (check `monumenta-server-management/gradle/libs.versions.toml` for the current hash):

```toml
[versions]
monumenta-common = "f231e8a-SNAPSHOT"   # update to current hash

[libraries]
monumenta-common = { module = "com.playmonumenta:monumenta-common", version.ref = "monumenta-common" }
```

**`plugin/build.gradle.kts`** — add `mavenLocal()` to repositories (the common plugin is published locally) and add the compile-only dependency:

```kotlin
repositories {
    mavenLocal()
    // ... existing repos ...
}

dependencies {
    compileOnly(libs.monumenta.common)
    compileOnly(libs.log4j.core)             // for org.apache.logging.log4j.Level in the static facade
    // ... existing dependencies ...
}
```

**If the plugin has tests**, also add:

```kotlin
// plugin/build.gradle.kts
testRuntimeOnly(libs.monumenta.common)
```

**Temporarily disable `-Werror`** in the root `build.gradle.kts`. The migration will introduce deprecation warnings at existing call sites; re-enable once all are resolved (Part 3):

```kotlin
allprojects {
    tasks.withType<JavaCompile> {
        // options.compilerArgs.add("-Werror")
    }
}
```

### Step 2 — Declare the runtime dependency

`monumenta-common` must be listed as a hard dependency so the server loads it before your plugin.

**Paper** — in the top-level `build.gradle.kts` `monumenta {}` block, add `"MonumentaCommon"` to the `depends` list:

```kotlin
monumenta {
    paper(
        "com.example.myplugin.MyPlugin",
        BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
        "1.20",
        depends = listOf("CommandAPI", "MonumentaCommon"),
        // ...
    )
}
```

**Velocity** — in the `@Plugin` annotation on the Velocity plugin class, add a `@Dependency`:

```java
@Plugin(
    id = "my-plugin",
    name = "MyPlugin",
    // ...
    dependencies = {
        @Dependency(id = "monumenta-common")
    }
)
```

### Step 3 — Delete old files

Remove:
- `src/.../CustomLogger.java`
- `src/.../commands/ChangeLogLevel.java` (or `ChangeLogLevelCommand.java`)

### Step 4 — Update the main plugin class

#### Paper (`JavaPlugin` subclass)

Remove:
```java
// imports
import com.example.myplugin.CustomLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

// field
private @MonotonicNonNull CustomLogger mLogger;

// getLogger() override
@Override
public Logger getLogger() { ... }
```

Add to the class:
```java
import com.example.myplugin.utils.MMLog;
```

Replace the `CustomLogger` construction and `ChangeLogLevel.register()` call in `onLoad()` with two lines:

```java
@Override
public void onLoad() {
    MMLog.init(getName()); // getName() == plugin.yml "name:" field — no string to keep in sync
    com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog());
    // registers: /changeloglevel MyPlugin TRACE|DEBUG|INFO|WARN|ERROR
    // permission: myplugin.changeloglevel
    // ... rest of onLoad
}
```

The logger name is taken directly from `JavaPlugin.getName()`, so it automatically matches the plugin.yml `name:` field. The facade's `init(String)` stores it internally (see Step 5).

#### Velocity (Velocity-only plugins)

For a Velocity-only plugin with no Paper counterpart, hold a `mLog` field directly on the plugin class. Pass the plugin name as a string literal — it lives right next to the `@Plugin` annotation so it is easy to keep in sync:

```java
import com.playmonumenta.common.MMLog;
import com.playmonumenta.common.MMLogVelocity;

public MMLog mLog;

// @Plugin(name = "MyPlugin", ...)
@Subscribe
public void onProxyInit(ProxyInitializeEvent event) {
    mLog = new MMLog("MyPlugin");
    MMLogVelocity.registerCommand(mLog, mServer.getCommandManager(), this);
    // registers: /changeloglevelvelocity MyPlugin TRACE|DEBUG|INFO|WARN|ERROR
}
```

#### Velocity (dual Paper+Velocity plugins)

Call `MMLog.init()` from the Velocity plugin constructor, then register the command. See Step 5 for the full facade template.

### Step 5 — Replace `utils/MMLog.java` (static facade)

The static facade holds a private `INSTANCE` of `com.playmonumenta.common.MMLog` and delegates all calls to it. All existing call sites (`MMLog.info("msg")` etc.) compile unchanged.

> **Code style note:** the project requires braces and indented bodies on all methods — no single-line `{ body }` forms. The delegation methods below are shown compact for readability.

**All plugins (Paper-only or dual Paper+Velocity):**

The static facade must not call `MMLogPaper` or `MMLogVelocity` directly — doing so would pull those platform-specific classes into the facade's bytecode, causing the same cross-platform `NoClassDefFoundError` described above. Instead, the facade only creates the instance; command registration is the caller's responsibility.

```java
package com.example.myplugin.utils;

import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

public class MMLog {
    private static @Nullable com.playmonumenta.common.MMLog INSTANCE = null;

    /**
     * Call once from the platform-specific plugin entry point before any logging.
     * On Paper pass {@code getName()}; on Velocity pass the string from the {@code @Plugin} annotation.
     */
    public static void init(String pluginId) {
        if (INSTANCE == null) {
            INSTANCE = new com.playmonumenta.common.MMLog(pluginId);
        }
    }

    /** For use in tests — pass Mockito.mock(com.playmonumenta.common.MMLog.class) */
    public static void init(com.playmonumenta.common.MMLog log) {
        INSTANCE = log;
    }

    /** Returns the instance for command registration from platform-specific code. */
    public static com.playmonumenta.common.MMLog getLog() {
        return get();
    }

    private static com.playmonumenta.common.MMLog get() {
        if (INSTANCE == null) {
            throw new RuntimeException("MyPlugin logger invoked before being initialized!");
        }
        return INSTANCE;
    }

    public static void setLevel(Level level)              { get().setLevel(level); }
    public static boolean isLevelEnabled(Level level)     { return get().isLevelEnabled(level); }

    public static void trace(Supplier<String> msg)        { get().trace(msg); }
    public static void trace(String msg)                  { get().trace(msg); }
    public static void trace(String msg, Throwable t)     { get().trace(msg, t); }
    public static void debug(Supplier<String> msg)        { get().debug(msg); }
    public static void debug(String msg)                  { get().debug(msg); }
    public static void debug(String msg, Throwable t)     { get().debug(msg, t); }
    public static void info(String msg)                   { get().info(msg); }
    public static void info(Supplier<String> msg)         { get().info(msg); }
    public static void warning(Supplier<String> msg)      { get().warning(msg); }
    public static void warning(String msg)                { get().warning(msg); }
    public static void warning(String msg, Throwable t)   { get().warning(msg, t); }
    public static void severe(Supplier<String> msg)       { get().severe(msg); }
    public static void severe(String msg)                 { get().severe(msg); }
    public static void severe(String msg, Throwable t)    { get().severe(msg, t); }

    /** @deprecated Use {@link #trace(Supplier)} instead. */
    @Deprecated public static void finest(Supplier<String> msg) { get().trace(msg); }
    /** @deprecated Use {@link #trace(String)} instead. */
    @Deprecated public static void finest(String msg)           { get().trace(msg); }
    /** @deprecated Use {@link #trace(Supplier)} instead. */
    @Deprecated public static void finer(Supplier<String> msg)  { get().trace(msg); }
    /** @deprecated Use {@link #trace(String)} instead. */
    @Deprecated public static void finer(String msg)            { get().trace(msg); }
    /** @deprecated Use {@link #debug(Supplier)} instead. */
    @Deprecated public static void fine(Supplier<String> msg)   { get().debug(msg); }
    /** @deprecated Use {@link #debug(String)} instead. */
    @Deprecated public static void fine(String msg)             { get().debug(msg); }
}
```

Then in the **Paper plugin entry point** (`onLoad`):

```java
MMLog.init(getName()); // getName() == plugin.yml "name:" field
com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog());
```

And in the **Velocity plugin constructor**:

```java
// @Plugin(name = "MyPlugin", ...)
MMLog.init("MyPlugin"); // string matches @Plugin(name=...) in this file
com.playmonumenta.common.MMLogVelocity.registerCommand(MMLog.getLog(), mServer.getCommandManager(), this);
```

On the Velocity plugin class, add `@Dependency(id = "monumenta-common")` alongside any existing dependencies:

```java
@Plugin(
    id = "my-plugin",
    // ...
    dependencies = {
        @Dependency(id = "monumenta-common"),
        @Dependency(id = "some-optional-dep", optional = true)
    }
)
```

### Step 6 — Update tests (if applicable)

If the plugin has tests that exercise code paths that log via `MMLog`, initialize the facade before running. Use the `init(com.playmonumenta.common.MMLog log)` overload (already in the facade template) with a Mockito mock:

```java
import com.example.myplugin.utils.MMLog;
import org.mockito.Mockito;

// In @BeforeAll / setUp():
MMLog.init(Mockito.mock(com.playmonumenta.common.MMLog.class));
```

**Why not construct a real instance?** `com.playmonumenta.common.MMLog`'s constructor is safe to call (it only calls `LogManager.getLogger()`). Direct construction works fine in tests — no platform classes are loaded by `MMLog` itself. The mock approach is also valid if you want to avoid any log4j2 initialization overhead.

---

## Part 3 -- Upgrading MMLog call sites

**Recommended order:** complete Parts 1 and 2 fully across all affected plugins and get them merged before starting Part 3. The deprecated aliases exist so this step can be deferred — touching call sites while the structural work is still in progress adds noise to diffs and increases the chance of merge conflicts.

Once the structural changes are merged, work through each plugin's deprecation warnings. There are three categories:

### Deprecated `MMLog` level methods

| Old call | New call |
|----------|----------|
| `MMLog.fine(msg)` | `MMLog.debug(msg)` |
| `MMLog.finer(msg)` | `MMLog.trace(msg)` |
| `MMLog.finest(msg)` | `MMLog.trace(msg)` |

Applies to both `String` and `Supplier<String>` variants.

### `plugin.getLogger().<level>(msg)`

Replace with the equivalent `MMLog` static method. Import `com.example.myplugin.utils.MMLog` (alphabetical order with other imports).

| Old | New |
|-----|-----|
| `plugin.getLogger().severe(msg)` | `MMLog.severe(msg)` |
| `plugin.getLogger().warning(msg)` | `MMLog.warning(msg)` |
| `plugin.getLogger().info(msg)` | `MMLog.info(msg)` |
| `plugin.getLogger().fine(msg)` | `MMLog.debug(msg)` |

### Exception logging

Pass the exception directly to `MMLog` instead of concatenating `getMessage()` and calling `printStackTrace()` separately.

```java
// Before
MMLog.severe("Failed to do thing: " + ex.getMessage());
ex.printStackTrace();

// After
MMLog.severe("Failed to do thing", ex);
```

If a bare `ex.printStackTrace()` exists with no preceding log call, convert it:

```java
// Before
ex.printStackTrace();

// After
MMLog.severe("Unexpected error", ex);
```

If the preceding call is a raw `logger.severe()` (e.g. early-init code before MMLog is available), use the JUL overload:

```java
// Before
e.printStackTrace();
logger.severe("Server version " + version + " is not supported!");

// After
logger.log(Level.SEVERE, "Server version " + version + " is not supported!", e);
// requires: import java.util.logging.Level;
```

Once all deprecation warnings are resolved, re-enable `-Werror` in the root `build.gradle.kts`.
