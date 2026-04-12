import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

dependencies {
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
}

monumenta {
	id("MonumentaCommon")
	name("MonumentaCommon")
	paper(
		"com.playmonumenta.common.MonumentaCommonPlugin",
		BukkitPluginDescription.PluginLoadOrder.POSTWORLD,
		"1.20"
	)
	gitPrefix("monumenta-common/")
}
