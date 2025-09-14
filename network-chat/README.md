# Monumenta Network Chat
Cross-server chat system created for Monumenta using redis data storage and rabbitmq message broker.

# Configuration File
This configuration file controls server-specific settings, and is useful for both testing and for disabling access to commands where doing so via permissions is not fully possible. For example, creative-mode servers where players may access command blocks, but are not trusted network-wide.
- `ReplaceHelpCommand` (default: `false`): Replaces the vanilla `/help` command with this Network Chat's `help` subcommand. In order for this to work, you must disable Bukkit's replacement of the vanilla `/help` command. For Spigot and its forks, you can edit `spigot.yml` here:
```yml
commands:
  replace-commands:
  - help
```
- `ChatCommandCreate` (default: `true`): Allows the creation of named chat channels from this Minecraft server. This does not disable creating anonymous whisper channels.
- `ChatCommandModify` (default: `true`): Allows modifying chat channels from this Minecraft server, such as controlling who can access a channel.
- `ChatCommandDelete` (default: `true`): Allows deleting named chat channels from this Minecraft server.
- `ChatRequiresPlayer` (default: `false`): If false, allows messages to be sent as command blocks and non-player entities. You may wish to set this to `true` on creative mode servers where players have operator status without being trusted network-wide.
- `SudoEnabled` (default: `false`): If true, allows for modifying another player's current chat channel and various chat settings via commands. Note that while this can be used to move someone out of a chat channel, this plugin provides no option to force a player to say anything in chat.

