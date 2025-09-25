import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

val mixinapi = libs.mixinapi

monumenta {
	id("MonumentaRedisSync")
	name("RedisSync")
	versionAdapterApi("adapter_api", paper = "1.18.2")
	versionAdapter("adapter_v1_20_R3", "1.20.4") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	javaSimple(":redis-sync:velocity")
	javaSimple(":redis-sync:core")
	pluginProject(":redis-sync:plugin")

	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI", "MonumentaNetworkRelay"),
	)

	gitPrefix("redis-sync/")
}
