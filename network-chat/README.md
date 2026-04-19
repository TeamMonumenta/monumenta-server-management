# Monumenta Network Chat

Cross-server chat system created for Monumenta using redis data storage and rabbitmq message broker.

## Configuration File

This configuration file controls server-specific settings, and is useful for both testing and for disabling access to
commands where doing so via permissions is not fully possible. For example, creative-mode servers where players may
access command blocks, but are not trusted network-wide.

- `ReplaceHelpCommand` (default: `false`): Replaces the vanilla `/help` command with this Network Chat's `help`
  subcommand. In order for this to work, you must disable Bukkit's replacement of the vanilla `/help` command. For
  Spigot and its forks, you can edit `spigot.yml` here:

```yml
commands:
	replace-commands:
		- help
```

- `ChatCommandCreate` (default: `true`): Allows the creation of named chat channels from this Minecraft server. This
  does not disable creating anonymous whisper channels.
- `ChatCommandModify` (default: `true`): Allows modifying chat channels from this Minecraft server, such as controlling
  who can access a channel.
- `ChatCommandDelete` (default: `true`): Allows deleting named chat channels from this Minecraft server.
- `ChatRequiresPlayer` (default: `false`): If false, allows messages to be sent as command blocks and non-player
  entities. You may wish to set this to `true` on creative mode servers where players have operator status without being
  trusted network-wide.
- `SudoEnabled` (default: `false`): If true, allows for modifying another player's current chat channel and various chat
  settings via commands. Note that while this can be used to move someone out of a chat channel, this plugin provides no
  option to force a player to say anything in chat.

## Chat Logging

Every received chat message is logged via a dedicated log4j logger named `NetworkChatLog`. By default (no extra
configuration needed), chat messages appear in the server console and in `logs/latest.log` alongside other server
output, tagged with the logger name:

    [18:27:07 INFO]: [NetworkChatLog] <l> Combustible >> test message

For server-local channels (`<l>` and world chat), the originating shard is prepended so that messages from other
servers are unambiguous:

    [18:27:07 INFO]: [NetworkChatLog] [shard1] <l> Combustible >> test message

By default every server logs chat from all shards (`ChatLogAllServers: true` in `config.yml`). Set it to `false` to
only log messages that are visible to players on this server — useful when all but one server should have a
filtered log and a single designated shard retains the full network-wide chat history.

To separate chat into its own log file and give it a distinct console label, add the following to your
`log4j2-shard.xml` (or equivalent log4j config):

Inside `<Appenders>`:

    <TerminalConsole name="ChatConsole">
        <PatternLayout pattern="[%d{HH:mm:ss} CHAT]: %msg%n"/>
    </TerminalConsole>
    <RollingRandomAccessFile name="ChatFile"
                             fileName="logs/chat/latest.log"
                             filePattern="logs/chat/%d{yyyy-MM-dd}-%i.log.gz">
        <PatternLayout pattern="[%d{yyyy-MM-dd HH:mm:ss}]: %msg%n"/>
        <Policies>
            <TimeBasedTriggeringPolicy />
            <SizeBasedTriggeringPolicy size="50 MB"/>
        </Policies>
        <DefaultRolloverStrategy max="1000"/>
    </RollingRandomAccessFile>

Inside `<Loggers>`:

    <Logger name="NetworkChatLog" level="info" additivity="false">
        <AppenderRef ref="ChatFile"/>
        <AppenderRef ref="ChatConsole"/>
    </Logger>

`additivity="false"` prevents chat from also propagating to the root logger, so it does not appear in
`logs/latest.log`. Chat is still visible on the server console with a `CHAT` level label instead of `INFO`:

    [18:11:36 INFO]: Combustible issued server command: /l test with command
    [18:11:36 CHAT]: <l> Combustible >> test with command

The dedicated chat log at `logs/chat/latest.log` rotates daily and at 50 MB, with gzip compression:

    [2026-04-18 18:11:36]: <l> Combustible >> test with command

