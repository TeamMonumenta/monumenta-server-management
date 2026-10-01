import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

repositories {
	mavenLocal()
}

val nbtapi = libs.nbtapi
val annotations = libs.annotations

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
		BukkitPluginYaml.PluginLoadOrder.POSTWORLD,
		"1.20", "1.20-R0.1-SNAPSHOT",
		depends = listOf("CommandAPI", "MonumentaCommon", "MonumentaNetworkRelay", "MonumentaRedisSync"),
		softDepends = listOf("NBTAPI")
	)
	versionAdapterApi("adapter_api") {
		dependencies {
			compileOnly(nbtapi)
			compileOnly(annotations)
		}
	}
	versionAdapter("adapter_v1_20_R3", "1.20.4-R0.1-SNAPSHOT") {
		dependencies {
			compileOnly(nbtapi)
			compileOnly(annotations)
		}
	}
	gitPrefix("world-management/")
}
