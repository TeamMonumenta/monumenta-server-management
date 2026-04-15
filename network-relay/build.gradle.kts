import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Werror")
}

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.viaversion.com")
}

dependencies {
	testImplementation(libs.snakeyaml)
	implementation(libs.rabbitmq)
	compileOnly(libs.monumenta.common)
	testRuntimeOnly(libs.monumenta.common)
	compileOnly(libs.annotations)
	compileOnly(libs.commandapi)
	compileOnly(libs.log4j.core)
	compileOnly(libs.placeholderapi)
	compileOnly(libs.velocity)
	compileOnly(libs.viaversion)
	annotationProcessor(libs.velocity)
	implementation(libs.slf4j)
}

testing {
	suites {
		val test by getting(JvmTestSuite::class) {
			useJUnitJupiter(libs.versions.junit.get())
		}
	}
}

monumenta {
	id("MonumentaNetworkRelay")
	name("MonumentaNetworkRelay")
	paper(
		"com.playmonumenta.networkrelay.NetworkRelay", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI", "MonumentaCommon"),
		softDepends = listOf(
			"PlaceholderAPI",
			"ViaVersion"
		)
	)
	gitPrefix("network-relay/")
}
