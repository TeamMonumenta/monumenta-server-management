# Monumenta Common

Shared reusable logic used by all Monumenta infrastructure plugins. Must be installed as a plugin on both Paper and Velocity — other Monumenta plugins depend on it directly at runtime.

---

## MMLog — Configuring Log Levels

Each Monumenta plugin has a named logger. By default all loggers follow the server's root log level (usually `INFO`). You can raise or lower the level for a specific plugin without restarting.

### Log level reference

| Level   | What you see |
|---------|--------------|
| `TRACE` | Everything, very verbose |
| `DEBUG` | Detailed diagnostic output |
| `INFO`  | Normal operational messages (default) |
| `WARN`  | Warnings only |
| `ERROR` | Errors only |

### Change a plugin's level at runtime (in-game command)

On a Paper server:
```
/changeloglevel <label> <LEVEL>
```

On a Velocity proxy:
```
/changeloglevelvelocity <label> <LEVEL>
```

Replace `<label>` with the plugin's full name — the same string used as the log4j2 logger name (e.g. `MonumentaNetworkRelay`, `ScriptedQuests`) — and `<LEVEL>` with one of the levels above. The change takes effect immediately and lasts until the server restarts. Requires the `<label>.changeloglevel` permission (all lowercase, e.g. `monumentanetworkrelay.changeloglevel`).

### Set a plugin's default level permanently (log4j2 config)

To persist a non-INFO default across restarts, add a `<Logger>` entry to the server's `log4j2.xml` inside the `<Loggers>` block:

```xml
<Loggers>
    <Logger name="ScriptedQuests" level="warn" additivity="false">
        <AppenderRef ref="Async"/>
    </Logger>
    <Root level="info">
        ...
    </Root>
</Loggers>
```

- `name` — the plugin's logger name; must exactly match the `<label>` used in the command (e.g. `MonumentaNetworkRelay`)
- `level` — the desired default level
- `additivity="false"` — prevents log messages appearing twice
- `AppenderRef ref="Async"` — routes output through the same appender chain as everything else

The runtime command still works on top of this; it overrides the XML-configured level until the next restart.

### Enabling DEBUG/TRACE output on the console

By default, Paper's `log4j2.xml` filters messages below `INFO` on the console `AppenderRef` lines, which prevents DEBUG/TRACE from appearing even when you lower a logger's level. To allow it, remove the `level="info"` attribute from those lines:

```xml
<!-- change this -->
<AppenderRef ref="TerminalConsole" level="info"/>
<AppenderRef ref="ServerGuiConsole" level="info"/>

<!-- to this -->
<AppenderRef ref="TerminalConsole"/>
<AppenderRef ref="ServerGuiConsole"/>
```

The root logger's own `level="info"` still suppresses DEBUG/TRACE from all other plugins by default — only loggers you have explicitly lowered will produce extra output.
