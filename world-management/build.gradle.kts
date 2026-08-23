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

monumenta {
	id("MonumentaWorldManagement")
	name("MonumentaWorldManagement")
	pluginProject("worldmanagement")
	paper(
		"com.playmonumenta.worlds.paper.WorldManagementPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20",
		depends = listOf("CommandAPI", "MonumentaCommon", "MonumentaNetworkRelay", "MonumentaRedisSync"),
		// TODO: NBTAPI is actually a hard dependency. But because of the way Monumenta shades it into the mixins project,
		//  it is always available to plugins, but it can't be listed as a hard dependency or loading will fail
		softDepends = listOf("NBTAPI")
	)

	versionAdapterApi("adapter_api", paper = "1.20.4")
	versionAdapterUnsupported("adapter_unsupported")
	versionAdapter("adapter_v1_20_R3", "1.20.4")
	gitPrefix("world-management/")
}
