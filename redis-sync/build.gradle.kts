import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

val mixinapi = libs.mixinapi


tasks {
    javadoc {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }
}

monumenta {
	id("MonumentaRedisSync")
	name("RedisSync")
	pluginProject("redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI", "MonumentaNetworkRelay"),
	)

	versionAdapterApi("adapter_api", paper = "1.18.2")
	versionAdapter("adapter_v1_20_R3", "1.20.4") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	javaSimple("redissync-example")
	gitPrefix("redis-sync/")
}
