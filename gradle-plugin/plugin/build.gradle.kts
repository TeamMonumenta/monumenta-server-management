import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val shadowImplementation: Configuration by configurations.creating

plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.jvm)
    id("com.github.johnrengelman.shadow") version "8.+"
}

group = "com.playmonumenta.gradle-config"
version = "3.6-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.playmonumenta.com/releases/")
}

dependencies {
    implementation(libs.jsch)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.junixsocket.core)
    implementation(libs.errorprone.plugin)
    implementation(libs.nullaway.plugin)
    implementation(libs.plugin.yml)
    implementation(libs.git.version)
    implementation(libs.shadow)
    implementation(libs.paperweight.userdev)

    shadowImplementation(libs.jsch)
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    val plugin by plugins.creating {
        id = group.toString()
        implementationClass = "com.playmonumenta.gradleconfig.MonumentaGradlePlugin"
    }
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    relocate("com.jcraft.jsch", "com.playmonumenta.gradleconfig.internal.jsch")
    archiveClassifier.set("")
    configurations = listOf(shadowImplementation)

    manifest {
        attributes(
            mapOf(
                Pair("Implementation-Version", version)
            )
        )
    }
}

artifacts {
	add("archives", shadowJarTask)
}

// stupid hack
tasks.named<Jar>("jar") {
	enabled = false
	dependsOn(shadowJarTask)
}

tasks.whenTaskAdded {
    if (name == "publishPluginJar" || name == "generateMetadataFileForPluginMavenPublication") {
        dependsOn(tasks.named("shadowJar"))
    }
}

publishing {
    repositories {
        maven {
            name = "MonumentaMaven"
            url = when (version.toString().endsWith("SNAPSHOT")) {
                true -> uri("https://maven.playmonumenta.com/snapshots")
                false -> uri("https://maven.playmonumenta.com/releases")
            }

            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                setArtifacts(listOf(shadowJarTask.get()))
            }
        }
    }
}

