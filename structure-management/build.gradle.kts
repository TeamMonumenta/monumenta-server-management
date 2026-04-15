import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	alias(libs.plugins.gradle.config)
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Werror")
}

repositories {
    mavenLocal()
}

dependencies {
    compileOnly(libs.monumenta.common)
    compileOnly(libs.log4j.core)
    compileOnly(libs.fawe.core)
    compileOnly(libs.fawe.bukkit) {
        isTransitive = false
    }
    compileOnly(libs.commandapi)
    compileOnly(libs.sq) {
        artifact {
            classifier = "all"
        }
    }
}

monumenta {
	id("MonumentaStructureManagement")
	name("MonumentaStructureManagement")
    paper(
        "com.playmonumenta.structures.StructuresPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
        depends = listOf("CommandAPI", "MonumentaCommon", "FastAsyncWorldEdit", "ScriptedQuests")
    )
	gitPrefix("structure-management/")
}
