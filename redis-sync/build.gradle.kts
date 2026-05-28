import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

val mixinapi = libs.mixinapi

monumenta {
	id("MonumentaRedisSync")
	name("MonumentaRedisSync")
	pluginProject("redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
        "1.20", "1.20-R0.1-SNAPSHOT",
		depends = listOf("CommandAPI", "MonumentaCommon", "MonumentaNetworkRelay"),
	)

	versionAdapterApi("adapter_api", paper = "1.18.2-R0.1-SNAPSHOT")
	versionAdapter("adapter_v1_20_R3", "1.20.4-R0.1-SNAPSHOT") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	versionAdapter("adapter_26_1_2", "26.1.2.build.+") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	gitPrefix("redis-sync/")
}
