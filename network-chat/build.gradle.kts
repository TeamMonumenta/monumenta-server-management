import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

repositories {
	mavenCentral()
	mavenLocal()
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.viaversion.com")
}

dependencies {
	implementation(libs.minimessage)
	implementation(libs.commons)
	compileOnly(libs.commandapi)
	compileOnly(libs.log4j.core)
	compileOnly(libs.monumenta.common)
	compileOnly(project(":network-relay"))
	compileOnly(project(":redis-sync:redissync"))
	compileOnly(libs.lettuce)
	compileOnly(libs.placeholderapi)
	compileOnly(libs.protocollib)
	compileOnly(libs.viaversion)
}

monumenta {
	id("MonumentaNetworkChat")
	name("MonumentaNetworkChat")
	paper(
		"com.playmonumenta.networkchat.NetworkChatPlugin", BukkitPluginYaml.PluginLoadOrder.POSTWORLD,
        "26.1.2", "26.1.2.build.+",
		depends = listOf(
			"CommandAPI",
			"MonumentaCommon",
			"MonumentaNetworkRelay",
			"MonumentaRedisSync",
			"PlaceholderAPI",
			"ProtocolLib"
		),
		softDepends = listOf("ViaVersion"),
		bootstrapper = "com.playmonumenta.networkchat.NetworkChatBootstrap",
	)
	gitPrefix("network-chat/")
}
