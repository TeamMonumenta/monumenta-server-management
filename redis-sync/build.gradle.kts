import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "3.+"
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
	pluginProject(":redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI"),
		softDepends = listOf("MonumentaNetworkRelay")
	)

	waterfall("com.playmonumenta.redissync.MonumentaRedisSyncBungee", "1.20")

	versionAdapterApi("adapter_api", paper = "1.18.2")
	versionAdapter("adapter_v1_20_R3", "1.20.4") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	javaSimple(":redissync-example")
}
