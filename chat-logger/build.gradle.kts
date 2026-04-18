plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
    id("application")
}

group = "com.playmonumenta"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.playmonumenta.com/releases")
    maven("https://maven.playmonumenta.com/snapshots")
}

dependencies {
    implementation(project(":monumenta-common"))
    implementation(project(":network-relay"))
    implementation(libs.minimessage)
    implementation("net.kyori:adventure-text-serializer-gson:4.11.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.log4j.core)
    implementation(libs.snakeyaml)
    compileOnly(libs.annotations)
}

application {
    mainClass.set("com.playmonumenta.chatlogger.ChatLoggerApp")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("chat-logger")
    archiveVersion.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.playmonumenta.chatlogger.ChatLoggerApp"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
