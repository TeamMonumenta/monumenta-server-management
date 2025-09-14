import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "1.+"
}

dependencies {
	compileOnly(libs.commandapi)
	compileOnly(libs.redissync) {
		artifact {
			classifier = "all"
		}
	}
	compileOnly(libs.gson)
}

monumenta {
	name("MonumentaWorldManagement")
	paper(
		"com.playmonumenta.worlds.paper.WorldManagementPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.19",
		depends = listOf("CommandAPI", "MonumentaRedisSync"),
		softDepends = listOf()
	)
	waterfall(
		"com.playmonumenta.worlds.bungee.WorldManagementBungeePlugin", "1.19",
		depends = listOf("MonumentaRedisSync")
	)
}
