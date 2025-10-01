# Monumenta Network Relay

This is a library plugin for Minecraft/Bukkit/Spigot/Paper that relays messages
between servers using RabbitMQ. This is a replacement for plugin messaging
channels or sockets, which both have significant limitations.

It was originally developed for [Monumenta](https://www.playmonumenta.com/), a
free community developed Complete-The-Monument MMORPG Minecraft server.

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
	<artifactId>monumenta-network-relay</artifactId>
	<version>1.3</version>
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
	compileOnly("com.playmonumenta:monumenta-network-relay:1.3")
}
```

Gradle (groovy):

```groovy
maven {
	name "monumenta"
	url "https://maven.playmonumenta.com/releases"
}

dependencies {
	compileOnly "com.playmonumenta:monumenta-network-relay:1.3"
}
```

## API

See the main API file here:
[src/main/java/com/playmonumenta/networkrelay/NetworkRelayAPI.java](src/main/java/com/playmonumenta/networkrelay/NetworkRelayAPI.java)
