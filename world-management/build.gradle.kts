import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

dependencies {
	compileOnly(libs.commandapi)
	compileOnly(project(":redis-sync:redissync"))
	// compileOnly(libs.gson)
}

monumenta {
	id("MonumentaWorldManagement")
	name("MonumentaWorldManagement")
	paper(
		"com.playmonumenta.worlds.paper.WorldManagementPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.19",
		depends = listOf("CommandAPI", "MonumentaRedisSync"),
		softDepends = listOf()
	)
}
