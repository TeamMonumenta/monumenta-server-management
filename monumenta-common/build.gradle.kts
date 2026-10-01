import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	gradlePluginPortal()
	maven("https://repo.mikeprimm.com/")
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

dependencies {
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
	compileOnly(libs.dynmap)
	compileOnly(libs.log4j.core)
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}

monumenta {
	id("MonumentaCommon")
	name("MonumentaCommon")
	paper(
		"com.playmonumenta.common.MonumentaCommonPlugin",
		BukkitPluginYaml.PluginLoadOrder.POSTWORLD,
		"1.20",
        "1.20-R0.1-SNAPSHOT",
		depends = listOf("CommandAPI"),
		softDepends = listOf("dynmap")
	)
	gitPrefix("monumenta-common/")
}
