import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Werror")
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
		"com.playmonumenta.networkchat.NetworkChatPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.19",
		depends = listOf(
			"CommandAPI",
			"MonumentaCommon",
			"MonumentaNetworkRelay",
			"MonumentaRedisSync",
			"PlaceholderAPI",
			"ProtocolLib"
		),
		softDepends = listOf("ViaVersion")
	)
	gitPrefix("network-chat/")
}
