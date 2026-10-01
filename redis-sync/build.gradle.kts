import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

val mixinapi = libs.mixinapi
val nbtapi = libs.nbtapi

monumenta {
	id("MonumentaRedisSync")
	name("MonumentaRedisSync")
	pluginProject("redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginYaml.PluginLoadOrder.POSTWORLD,
        "26.1.2", "26.1.2.build.+",
		depends = listOf("CommandAPI", "MonumentaCommon", "MonumentaNetworkRelay"),
	)

	versionAdapterApi("adapter_api", paper = "26.1.2.build.+") {
		dependencies {
			compileOnly(nbtapi)
		}
	}
	versionAdapter("adapter_26_1_2", "26.1.2.build.+") {
		dependencies {
			compileOnly(mixinapi)
			compileOnly(nbtapi)
		}
	}
	gitPrefix("redis-sync/")
}
