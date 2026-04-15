import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	mavenLocal()
}

dependencies {
	compileOnly(libs.monumenta.common)
	compileOnly(libs.log4j.core)
	compileOnly(libs.commandapi)
	compileOnly(project(":network-relay"))
	compileOnly(project(":redis-sync:redissync"))
}

monumenta {
	id("MonumentaWorldManagement")
	name("MonumentaWorldManagement")
	paper(
		"com.playmonumenta.worlds.paper.WorldManagementPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20",
		depends = listOf("CommandAPI", "MonumentaCommon", "MonumentaRedisSync"),
		softDepends = listOf("MonumentaNetworkRelay")
	)
	gitPrefix("world-management/")
}
