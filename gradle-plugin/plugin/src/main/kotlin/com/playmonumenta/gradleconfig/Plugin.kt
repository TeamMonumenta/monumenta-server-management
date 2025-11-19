package com.playmonumenta.gradleconfig

import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MonumentaGradlePlugin : Plugin<Project> {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("MonumentaGradlePlugin")
    }

    override fun apply(target: Project) {
        target.charset("UTF-8")
        target.applyPlugin("java-library")

        target.extensions.add("monumenta", MonumentaExtensionImpl(target))
    }
}
