plugins {
	java
}

group = "com.playmonumenta"
version = "0.0.1"
description = "redissync-example"

repositories {
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
	maven("https://maven.playmonumenta.com/releases/")
	maven("https://maven.playmonumenta.com/snapshots/")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
	compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
	annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
	compileOnly("io.lettuce:lettuce-core:6.3.2.RELEASE")
	compileOnly("com.playmonumenta:redissync:5.+:all")
}
