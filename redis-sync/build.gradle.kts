import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

val mixinapi = libs.mixinapi

monumenta {
	id("MonumentaRedisSync")
	name("RedisSync")
	versionAdapterApi("plugin", paper = "1.20.4") {
		repositories {
			mavenLocal()
		}
	}
	versionAdapter("adapter_v1_20_R3", "1.20.4") {
		repositories {
			mavenLocal()
		}

		dependencies {
			compileOnly(mixinapi)
		}
	}
	javaSimple(":redis-sync:velocity")
	javaSimple(":redis-sync:common")
	pluginProject(":redis-sync:plugin")

	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI", "MonumentaNetworkRelay"),
	)

	gitPrefix("redis-sync/")
}
