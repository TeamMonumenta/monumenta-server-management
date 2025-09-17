import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

repositories {
	mavenCentral()
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.viaversion.com")
}

dependencies {
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.snakeyaml)
	implementation(libs.rabbitmq)
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
	compileOnly(libs.placeholderapi)
	compileOnly(libs.velocity)
	compileOnly(libs.viaversion)
	annotationProcessor(libs.velocity)
	implementation(libs.slf4j)
}

monumenta {
	id("MonumentaNetworkRelay")
	name("NetworkRelay")
	paper(
		"com.playmonumenta.networkrelay.NetworkRelay", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI"),
		softDepends = listOf(
			"PlaceholderAPI",
			"ViaVersion"
		)
	)
	gitPrefix("network-relay/")
}
