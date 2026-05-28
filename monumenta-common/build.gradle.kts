import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	// TODO: revert before merge
	// options.compilerArgs.add("-Werror")
}

dependencies {
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
	compileOnly(libs.log4j.core)
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}

monumenta {
	id("MonumentaCommon")
	name("MonumentaCommon")
	paper(
		"com.playmonumenta.common.MonumentaCommonPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20",
        "1.20-R0.1-SNAPSHOT"
	)
	gitPrefix("monumenta-common/")
}
