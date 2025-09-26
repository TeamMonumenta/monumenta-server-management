# Monumenta Server Management

This is the new home for the plugins that [Monumenta](https://www.playmonumenta.com/) develops and encourages other
servers to take advantage of. Here's a summary of what each one does:

Monumenta is a free community developed Complete-The-Monument MMORPG Minecraft server.

## Monumenta Network Relay

This allows Minecraft servers and proxies to send each other messages over RabbitMQ.

Hard Dependencies:

- CommandAPI
  Optional Dependencies:
- PlaceholderAPI
- ViaVersion

## Monumenta Redis Sync

This allows Minecraft servers to communicate with Redis, storing both player files and arbitrary plugin data.

Hard Dependencies:

- CommandAPI
- MonumentaNetworkRelay

## Monumenta Network Chat

This is a cross-server chat plugin, with customizable chat filters and moderation support.

Hard Dependencies:

- CommandAPI
- MonumentaNetworkRelay
- MonumentaRedisSync
- PlaceholderAPI
- ProtocolLib
  Optional Dependencies:
- ViaVersion

## Monumenta Structure Management

This provides the backbone of Monumenta's respawning Point-of-Interest system using FastAsyncWorldEdit (FAWE).

Hard Dependencies:

- CommandAPI
- FastAsyncWorldEdit
- ScriptedQuests

## monumenta-world-management

This provides tools for loading, unloading, copying, and moving players between worlds.

Hard Dependencies:

- CommandAPI
- MonumentaRedisSync
