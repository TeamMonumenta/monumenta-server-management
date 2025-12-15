# Monumenta Server Management

This is the new home for the plugins that [Monumenta](https://www.playmonumenta.com/) develops and encourages other
servers to take advantage of. Monumenta is a free, community-developed Complete-The-Monument MMORPG Minecraft server.

This project consists of several modules, which are independent plugins:

| Module                                                   | Description                                |
|----------------------------------------------------------|--------------------------------------------|
| [Network Relay](./network-relay/README.md)               | A cross-server message plugin w/ RabbitMQ. |
| [Redis Sync](./redis-sync/README.md)                     | Redis backend for playerdata.              |
| [Network Chat](./network-chat/README.md)                 | A cross-server chat plugin.                |
| [Structure Management](./structure-management/README.md) | Monumenta's respawning POI w/ FAWE.        |
| [World Management](./world-management/README.md)         | Tools for managing worlds.                 |

## Downloading

Dependencies available on [maven](https://maven.playmonumenta.com/#/).

**Maven**

```xml

<repository>
	<id>monumenta</id>
	<name>Monumenta Maven Repo</name>
	<url>https://maven.playmonumenta.com/releases</url>
</repository>
```

**Gradle (groovy)**

```groovy
maven {
	name "monumenta"
	url "https://maven.playmonumenta.com/releases"
}
```

**Gradle (kotlin)**

```groovy
maven {
	name = "monumenta"
	url = uri("https://maven.playmonumenta.com/releases")
}
```
