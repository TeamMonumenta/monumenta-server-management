# Monumenta Network Relay
This is a library plugin for Minecraft/Bukkit/Spigot/Paper that relays messages
between servers using RabbitMQ. This is a replacement for plugin messaging
channels or sockets, which both have significant limitations.

It was originally developed for [Monumenta](https://www.playmonumenta.com/), a
free community developed Complete-The-Monument MMORPG Minecraft server.

## Download
You can download the latest version of this plugin from [GitHub Packages](https://github.com/TeamMonumenta/monumenta-network-relay/packages).

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

## Licensing
The main plugin here (MonumentaNetworkRelay, in the src directory), is free to
use as a dependency in your plugins without restriction or obligation. If you
modify or create derivative work based on the code in this plugin (i.e. if you
do anything other than just depend on this plugin and use its API), you must
release that code also with an AGPL-3 compatible license.

You may also distribute this plugin in binary/compiled format (i.e. .jar) so
long as it retains the monumenta-network-relay name.

The example plugin under the network-chat-example folder is released without
restriction under the [WTFPL](http://www.wtfpl.net/) license.  You may do
whatever you want with this example code.
