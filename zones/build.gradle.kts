import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	gradlePluginPortal()
	maven("https://maven.playmonumenta.com/releases/")
	maven("https://repo.mikeprimm.com/")
}

dependencies {
	compileOnly(libs.commandapi)
	compileOnly(libs.dynmap)
	// compileOnly(libs.gson)
}

monumenta {
	id("MonumentaZones")
	name("MonumentaZones")
	paper(
		"com.playmonumenta.zones.ZonesPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20",
		depends = listOf("CommandAPI"),
		softDepends = listOf("dynmap")
	)
	gitPrefix("zones/")
}
