# network-relay Tests

## Running

```bash
./gradlew :network-relay:test
```

## Test Summary

All tests cover config loading in `com.playmonumenta.networkrelay.config`.

| Class | Method | What it tests |
|---|---|---|
| `CommonConfigTest` | `loadCommon` | `CommonConfig` field defaults on construction; defaults preserved when loading from empty map; invalid values (bad log-level string, negative intervals) fall back to defaults; non-default values are read correctly |
| `CommonConfigTest` | `getString` | `CommonConfig.getString()` helper: missing key returns fallback; wrong type returns fallback; empty string returns fallback unless `emptyAllowed=true`; correct string value returned |
| `CommonConfigTest` | `getBoolean` | `CommonConfig.getBoolean()` helper: missing key, wrong type return fallback; correct `true`/`false` values returned |
| `CommonConfigTest` | `getInt` | `CommonConfig.getInt()` helper: missing key, wrong types (String, Boolean, Long) return fallback; correct int values (including `MIN_VALUE`/`MAX_VALUE`) returned |
| `CommonConfigTest` | `getLong` | `CommonConfig.getLong()` helper: missing key, wrong types return fallback; Integer is accepted as equivalent type; correct long values returned |
| `BukkitConfigTest` | `loadConfigTest` | `BukkitConfig` loading from: missing file (falls back to embedded default), empty YAML, non-default YAML, empty YAML with non-default fallback, non-default YAML with empty fallback — verifies all fields including Bukkit-specific `broadcastCommandSendingEnabled`, `broadcastCommandReceivingEnabled`, `serverAddress` |
| `BungeeConfigTest` | `loadConfigTest` | Same file-loading scenarios as BukkitConfigTest but for `BungeeConfig`, verifying Bungee-specific fields: `runReceivedCommands`, `autoRegisterServersToBungee`, `autoUnregisterInactiveServersFromBungee` |
| `GenericConfigTest` | `loadConfigTest` | Same file-loading scenarios for `GenericConfig` (common fields only, no platform-specific extras) |
