package com.playmonumenta.gradleconfig

import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.language.jvm.tasks.ProcessResources
import java.net.URI

internal fun RepositoryHandler.maven(maven: String) {
    maven { it.url = URI(maven) }
}

internal fun Project.applyPlugin(vararg names: String) {
    names.forEach {
        apply(mapOf("plugin" to it))
    }
}

internal inline fun <T, reified S : T> DomainObjectCollection<T>.withType(
    noinline configureAction: S.() -> Unit
): DomainObjectCollection<S> = withType(S::class.java, configureAction)

internal fun CompileOptions.errorproneWrap(action: ErrorProneOptions.() -> Unit) = errorprone(action)

internal fun Project.embeddedResource(path: String): TextResource {
    return resources.text.fromString(MonumentaGradlePlugin::class.java.getResource(path)?.readText()!!)
}

internal fun Project.addCompileOnly(deps: Any) {
    with(project.dependencies) {
        add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, deps)
    }
}

internal fun Project.addImplementation(deps: Any) {
    with(project.dependencies) {
        add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, deps)
    }
}

internal fun Project.addRuntimeOnly(deps: Any) {
    with(project.dependencies) {
        add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, deps)
    }
}

internal fun Project.charset(name: String) {
    tasks.withType<_, JavaCompile> {
        options.encoding = name
    }

    tasks.withType<_, Javadoc> {
        options.encoding = name
    }

    tasks.withType<_, ProcessResources> {
        filteringCharset = name
    }
}

internal inline fun <reified T> ExtensionContainer.withType(f: T.() -> Unit) {
    getByType(T::class.java).apply(f)
}
