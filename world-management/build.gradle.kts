import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Werror")
}

repositories {
	mavenLocal()
}

dependencies {
	compileOnly(libs.monumenta.common)
	compileOnly(libs.log4j.core)
	compileOnly(libs.commandapi)
	compileOnly(libs.nbtapi)
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
		// TODO: NBTAPI is actually a hard dependency. But because of the way Monumenta shades it into the mixins project,
		//  it is always available to plugins, but it can't be listed as a hard dependency or loading will fail
		softDepends = listOf("MonumentaNetworkRelay", "NBTAPI")
	)
	gitPrefix("world-management/")
}
