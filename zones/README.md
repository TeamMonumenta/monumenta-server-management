# monumenta-zones

Spatial zone manager with O(log(n)) lookup performance. Dependency for both ScriptedQuests and MonumentaStructureManagement.

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
	<artifactId>zones</artifactId>
	<version>1.0.0</version>
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
	compileOnly("com.playmonumenta:zones:1.0.0")
}
```

Gradle (groovy):

```groovy
maven {
	name "monumenta"
	url "https://maven.playmonumenta.com/releases"
}

dependencies {
	compileOnly "com.playmonumenta:zones:1.0.0"
}
```
