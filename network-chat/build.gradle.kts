import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	mavenCentral()
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.viaversion.com")
}

dependencies {
	implementation(libs.minimessage)
	implementation(libs.commons)
	compileOnly(libs.commandapi)
	compileOnly(project(":network-relay"))
	compileOnly(project(":redis-sync:plugin"))
	compileOnly(libs.lettuce)
	compileOnly(libs.placeholderapi)
	compileOnly(libs.protocollib)
	compileOnly(libs.viaversion)
}

monumenta {
	id("MonumentaNetworkChat")
	name("NetworkChat")
	paper(
		"com.playmonumenta.networkchat.NetworkChatPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.19",
		depends = listOf(
			"CommandAPI",
			"MonumentaNetworkRelay",
			"MonumentaRedisSync",
			"PlaceholderAPI",
			"ProtocolLib"
		),
		softDepends = listOf("ViaVersion")
	)
	gitPrefix("network-chat/")
}
