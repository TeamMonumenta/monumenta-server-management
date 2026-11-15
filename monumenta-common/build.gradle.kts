import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	gradlePluginPortal()
	maven("https://repo.mikeprimm.com/")
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Werror")
}

dependencies {
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
	compileOnly(libs.dynmap)
	compileOnly(libs.log4j.core)
}

monumenta {
	id("MonumentaCommon")
	name("MonumentaCommon")
	paper(
		"com.playmonumenta.common.MonumentaCommonPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20",
		depends = listOf("CommandAPI"),
		softDepends = listOf("dynmap")
	)
	velocity(libs.versions.velocity.get(), "com.playmonumenta.common")
	gitPrefix("monumenta-common/")
}
