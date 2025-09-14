# Monumenta Redis Sync

## Introduction

The purpose of this plugin is to manage player data for a large number of
minecraft servers by storing it in a Redis database.

When a user logs in, instead of reading their data from the world, it instead
loads it from Redis. And, likewise, when the player logs out or their data is
saved, that data get stored into Redis.

This plugin also provides commands to transfer players between servers on a
bungeecord network, allowing a game world to be broken up across many minecraft
servers for performance or abstraction purposes.

Player data is versioned, by default saving the previous 20 versions. This
allows you to roll back a player to a previous save point. Additionally, there
is a "stash" mechanism that lets you temporarily save your current player data
and easily return to it later - or load a different player's stashed data as if
it was your own.

## Current status

This plugin is functional and is being used in production on the Monumenta
server network spanning 20+ servers and hundreds of players. It is still a
little rough around the edges, missing some nice-to-have features.

Here's a list of currently supported things:
- Save playerdata, advancements, and scoreboard data to/from Redis
- Function & command block accessible /transferserver command to move players
  between servers on the same bungeecord network
- /playerhistory to inspect a player's save history
- /playerrollback to roll them back to a previous version
- /stash [put|get|info] to temporarily save and load your playerdata
- A plugin API to use those features directly from other plugins
- A player transfer event, which allows plugins to manipulate player data prior
  to a player transferring servers
- Support for multiple different "domains". Each server in the same domain will
  use the same player data. No data transfer is possible between domains
  (useful if you have different types of servers)
- Exposes the Redis API (via Lettuce) for access by other plugins
- Also loads on Bungeecord, though currently does nothing there besides provide
  access to the Lettuce API
- An API to allow plugins to save additional information about the player for
  transfer
- An API and in-game commands to access a global redis scoreboard (the 'rboard')
  which lets you access and share data from different minecraft servers
  concurrently. This is particularly useful for data that needs to be accessed
  from both bungeecord and minecraft servers simultaneously.

Planned features:
- Automatic config file creation
- Storage of player stats data

## Example Dependant Plugin

See the [example](example) directory for a complete example on how you might
use this plugin as a dependency of one of your plugins, either for Paper or
bungeecord.

## Dependencies

This plugin requires a Paper-based minecraft server, compiled with specific
patches to enable high-performance access to player data save/load events. This
is a significantly more cumbersome requirement than most plugins. The `1.20.4`
version loads these patches at runtime with the Mixin bytecode transformation
framework, which requires using a [fabric mod loader for paper](https://github.com/Floweynt/fabric-paper-modloader)
as the server software. The mixin implementation is [here](https://github.com/TeamMonumenta/monumenta-mixins).

A fork of paper with these patches (and others) can be found here:
https://github.com/TeamMonumenta/monumenta-paperfork

If you are interested in getting this working / trying it out yourself, join
the Monumenta Discord (https://discord.gg/eep9qcu) and message @Combustible.

This plugin also requires CommandAPI 6.0. Other versions might also work, worth
testing. If you try with a version that doesn't work (log errors, commands
don't work, etc.) it's not going to damage your player data.

The current version of this plugin requires Minecraft 1.18.2. Other versions
could be easily supported, just ask.

## Maven dependency

## Maven dependency
```xml
<repository>
	<id>monumenta</id>
	<name>Monumenta Maven Repo</name>
	<url>https://maven.playmonumenta.com/releases</url>
</repository>
<dependencies>
	<dependency>
		<groupId>com.playmonumenta</groupId>
		<artifactId>redissync</artifactId>
		<version>4.1</version>
		<scope>provided</scope>
	</dependency>
</dependencies>
```
Gradle (kotlin):
```kts
maven {
    name = "monumenta"
    url = uri("https://maven.playmonumenta.com/releases")
}

dependencies {
	compileOnly("com.playmonumenta:redissync:4.1")
}
```
Gradle (groovy):
```groovy
maven {
    name "monumenta"
    url "https://maven.playmonumenta.com/releases"
}

dependencies {
	compileOnly "com.playmonumenta:redissync:4.1"
}
```

## Download

You can download the Monumenta Redis Sync plugin from [GitHub Packages](https://github.com/TeamMonumenta/monumenta-redis-sync/packages).
