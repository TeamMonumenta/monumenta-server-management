package com.playmonumenta.gradleconfig

import com.palantir.gradle.gitversion.VersionDetails
import com.playmonumenta.gradleconfig.ssh.easySetup
import groovy.lang.Closure
import io.papermc.paperweight.userdev.PaperweightUserDependenciesExtension
import net.ltgt.gradle.errorprone.CheckSeverity
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.bungee.BungeePluginDescription
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import java.net.URI

private fun setupProject(project: Project, target: Project, javadoc: Boolean) {
    project.applyPlugin(
        "pmd",
        "java-library",
        "checkstyle",
        "net.ltgt.errorprone",
        "net.ltgt.nullaway",
    )

    with(project.repositories) {
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.maven.apache.org/maven2/")
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://maven.playmonumenta.com/releases")
        maven("https://maven.playmonumenta.com/snapshots")
    }

    with(project.dependencies) {
        add("errorprone", "com.google.errorprone:error_prone_core:2.29.1")
        add("errorprone", "com.uber.nullaway:nullaway:0.10.18")
    }

    project.tasks.withType<_, JavaCompile> {
        options.compilerArgs.add("-Xmaxwarns")
        options.compilerArgs.add("10000")
        options.compilerArgs.add("-Xlint:deprecation")

        options.errorproneWrap {
            option("NullAway:AnnotatedPackages", "com.playmonumenta")
            allErrorsAsWarnings.set(true)

            /*** Disabled checks ***/
            // This is the primary way a lot of exceptions are handled
            check("CatchAndPrintStackTrace", CheckSeverity.OFF)
            // This one is dumb and doesn't let you check return values with .whenComplete()
            check("FutureReturnValueIgnored", CheckSeverity.OFF)
            // Would like to turn this on but we'd have to annotate a bunch of base classes
            check("ImmutableEnumChecker", CheckSeverity.OFF)
            // Very few locks in our code, those that we have are simple and refactoring like this would be ugly
            check("LockNotBeforeTry", CheckSeverity.OFF)
            // We have tons of these on purpose
            check("StaticAssignmentInConstructor", CheckSeverity.OFF)
            // We have a lot of string splits too which are fine for this use
            check("StringSplitter", CheckSeverity.OFF)
            // These are bad practice but annoying to refactor and low risk of actual bugs
            check("MutablePublicArray", CheckSeverity.OFF)
            // This seems way overkill
            check("InlineMeSuggester", CheckSeverity.OFF)
        }
    }

    with(project.tasks.getByName("javadoc") as Javadoc) {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    with(project.extensions.getByType(PmdExtension::class.java)) {
        isConsoleOutput = true
        toolVersion = "7.13.0"
        ruleSetConfig = project.embeddedResource("/pmd-ruleset.xml")
        isIgnoreFailures = true
    }

    with(project.extensions.getByType(CheckstyleExtension::class.java)) {
        config = project.embeddedResource("/checkstyle.xml")
    }

    project.tasks.withType(Checkstyle::class.java) {
        it.minHeapSize.set("1g")
        it.maxHeapSize.set("1g")
    }

    project.charset("UTF-8")
    if (project.description == null) {
        project.description = project.path.substring(1)
    }
    project.group = target.group
    project.version = target.version

    with(project.extensions.getByType(JavaPluginExtension::class.java)) {
        if (javadoc) {
            withJavadocJar()
        }

        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

private fun setupVersion(project: Project, prefix: String?) {
    project.applyPlugin("com.palantir.git-version")
    val extra = project.extensions.getByType(ExtraPropertiesExtension::class.java)

    val gitVersion = extra.get("gitVersion") as Closure<String>
    val versionDetails = extra.get("versionDetails") as Closure<VersionDetails>

    project.group = "com.playmonumenta"

    val gitResult = prefix?.let { gitVersion.call(mapOf("prefix" to it)) } ?: gitVersion.call(prefix)
    project.version = gitResult + (if (versionDetails.call().isCleanTag) "" else "-SNAPSHOT")
}

internal class MonumentaExtensionImpl(private val target: Project) : MonumentaExtension {
    init {
        target.afterEvaluate { afterEvaluate() }
    }

    private var snapshotUrl: String = "https://maven.playmonumenta.com/snapshots"
    private var releasesUrl: String = "https://maven.playmonumenta.com/releases"
    private var mavenUsername: String? = System.getenv("USERNAME")
    private var mavenPassword: String? = System.getenv("TOKEN")
    private var isBukkitConfigured: Boolean = false
    private var isBungeeConfigured: Boolean = false
    private var pluginName: String? = null
    private var pluginId: String? = null
    private var disableMaven: Boolean = false
    private var disableJavadoc: Boolean = false

    private val deferActions: MutableList<() -> Unit> = ArrayList()

    private var pluginProject: Project = target
    private var hasAdapter = false
    private var adapterApiProject: Project? = null
    private var adapterApiPaperDep: String? = null
    private var adapterUnsupportedProject: Project? = null
    private val simpleProjects: MutableList<Project> = ArrayList()
    private val adapterImplementations: MutableList<Pair<Project, String>> = ArrayList()
    private var gitPrefix: String? = null

    private fun findSubproject(name: String, config: Project.() -> Unit): Project {
        val res = target.findProject(name) ?: throw IllegalArgumentException("Unknown subproject '$name'")
        deferActions {
            config(res)
        }

        return res
    }

    private fun deferActions(action: () -> Unit) = deferActions.add(action)

    override fun snapshotRepo(url: String) {
        snapshotUrl = url
    }

    override fun releasesRepo(url: String) {
        releasesUrl = url
    }

    override fun id(id: String) {
        if (pluginId != null) {
            throw IllegalStateException("id(...) can only be called once")
        }

        this.pluginId = id
    }

    override fun name(name: String) {
        if (pluginName != null) {
            throw IllegalStateException("name(...) can only be called once")
        }

        this.pluginName = name
    }

    override fun disableMaven() {
        disableMaven = true
    }

    override fun disableJavadoc() {
        disableJavadoc = true
    }

    override fun pluginProject(path: String, config: Project.() -> Unit) {
        pluginProject = findSubproject(path, config)
    }

    override fun publishingCredentials(name: String, token: String) {
        mavenUsername = name
        mavenPassword = token
    }

    override fun versionAdapterApi(name: String, paper: String?, config: Project.() -> Unit) {
        hasAdapter = true
        adapterApiProject = findSubproject(name, config)
        adapterApiPaperDep = paper
    }

    override fun versionAdapterUnsupported(name: String, config: Project.() -> Unit) {
        hasAdapter = true
        adapterUnsupportedProject = findSubproject(name, config)
    }

    override fun versionAdapter(name: String, devBundle: String, config: Project.() -> Unit) {
        hasAdapter = true
        adapterImplementations.add(Pair(findSubproject(name, config), devBundle))
    }

    override fun paper(
        main: String,
        order: BukkitPluginDescription.PluginLoadOrder,
        apiVersion: String,
        authors: List<String>,
        depends: List<String>,
        softDepends: List<String>,
        apiJarVersion: String,
        action: BukkitPluginDescription.() -> Unit
    ) {
        if (isBukkitConfigured) {
            throw IllegalStateException("Bukkit can't be configured multiple times")
        }

        if (this.pluginId == null) {
            throw IllegalStateException("id(...) must be called first")
        }

        isBukkitConfigured = true

        deferActions {
            pluginProject.applyPlugin("net.minecrell.plugin-yml.bukkit")
            pluginProject.extensions.getByType(BukkitPluginDescription::class.java).let {
                it.load = order
                it.main = main
                it.apiVersion = apiVersion
                it.name = pluginId
                it.authors = authors
                it.depend = depends
                it.softDepend = softDepends
                action(it)
            }

            pluginProject.addCompileOnly("io.papermc.paper:paper-api:$apiJarVersion")
        }
    }

    override fun waterfall(
        main: String,
        apiVersion: String,
        authors: List<String>,
        depends: List<String>,
        softDepends: List<String>
    ) {
        if (isBungeeConfigured) {
            throw IllegalStateException("Bungee can't be configured multiple times")
        }

        if (this.pluginId == null) {
            throw IllegalStateException("id(...) must be called first")
        }

        deferActions {
            pluginProject.applyPlugin("net.minecrell.plugin-yml.bungee")
            with(pluginProject.extensions.getByType(BungeePluginDescription::class.java)) {
                this.main = main
                this.name = pluginId
                this.author = authors.joinToString(", ")
                this.depends = setOf(*depends.toTypedArray())
                this.softDepends = setOf(*softDepends.toTypedArray())
                this.version = apiVersion
            }

            pluginProject.addCompileOnly("io.github.waterfallmc:waterfall-api:$apiVersion-R0.1-SNAPSHOT")
        }
    }

    private fun configureVersionAdapterWithApi(project: Project) {
        if (project == pluginProject) {
            return
        }

        if (adapterApiPaperDep != null) {
            project.addCompileOnly("io.papermc.paper:paper-api:$adapterApiPaperDep-R0.1-SNAPSHOT")
        }

        pluginProject.addImplementation(
            pluginProject.dependencies.project(
                mapOf(
                    "path" to project.path,
                )
            )
        )
    }

    private fun configurePaperweightVersionAdapter(project: Project, apiProject: Project, devBundle: String) {
        project.addImplementation(apiProject)
        project.applyPlugin("com.playmonumenta.paperweight-aw.userdev")

        project.dependencies.extensions.getByType(PaperweightUserDependenciesExtension::class.java)
            .paperDevBundle("${devBundle}-R0.1-SNAPSHOT")

        pluginProject.addRuntimeOnly(
            pluginProject.dependencies.project(
                mapOf(
                    "path" to project.path,
                    "configuration" to "reobf"
                )
            )
        )
    }

    private fun configureVersionAdapters(apiProject: Project) {
        configureVersionAdapterWithApi(apiProject)

        val unsupportedProject = adapterUnsupportedProject

        if (unsupportedProject == null) {
            MonumentaGradlePlugin.LOGGER.warn("Version adapter is enabled, but no unsupported adapter found. Consider adding one.")
        } else {
            configureVersionAdapterWithApi(unsupportedProject)
            unsupportedProject.addImplementation(apiProject)
        }

        if (adapterImplementations.isEmpty()) {
            MonumentaGradlePlugin.LOGGER.warn("Version adapter is enabled, but no implementation found. Consider adding one.")
        }

        adapterImplementations.forEach {
            configurePaperweightVersionAdapter(it.first, apiProject, it.second)
        }
    }

    override fun javaSimple(name: String, config: Project.() -> Unit) {
        simpleProjects.add(findSubproject(name, config))
    }

    private fun afterEvaluate() {
        setupVersion(target, gitPrefix)

        if (this.pluginName == null) {
            throw IllegalStateException("name(...) must be called")
        }

        if (this.pluginId == null) {
            throw IllegalStateException("id(...) must be called")
        }

        pluginProject.applyPlugin(
            "com.github.johnrengelman.shadow",
            "maven-publish"
        )

        setOf(
            pluginProject,
            adapterApiProject,
            adapterUnsupportedProject,
            *simpleProjects.toTypedArray(),
            *adapterImplementations.map { it.first }.toTypedArray()
        ).filterNotNull().forEach { setupProject(it, target, !disableJavadoc) }

        if (hasAdapter) {
            val apiProject = adapterApiProject
                ?: throw IllegalStateException("A project with version adapters must specific a version adapter API!")
            configureVersionAdapters(apiProject)
        }

        deferActions.forEach { it() }

        if (!disableMaven) {
            with(pluginProject.extensions.getByType(PublishingExtension::class.java)) {
                publications { container ->
                    container.create("maven", MavenPublication::class.java) {
                        if (hasAdapter) {
                            it.artifact(pluginProject.tasks.getByName("shadowJar"))
                            it.artifact(pluginProject.tasks.getByName("javadocJar"))
                            it.artifact(pluginProject.tasks.getByName("sourcesJar"))
                        } else {
                            it.from(pluginProject.components.getByName("java"))
                        }
                    }
                }
                repositories { repo ->
                    repo.maven { maven ->
                        maven.name = "MainMaven"
                        maven.url =
                            URI(if (pluginProject.version.toString().endsWith("SNAPSHOT")) snapshotUrl else releasesUrl)
                        maven.credentials { cred ->
                            cred.username = mavenUsername
                            cred.password = mavenPassword
                        }
                    }
                    repo.mavenLocal()
                }
            }
        }

        with(pluginProject.extensions.getByType(BasePluginExtension::class.java)) {
            archivesName.set(pluginName)
        }

        easySetup(pluginProject, pluginProject.tasks.getByName("shadowJar") as Jar)
    }

    override fun gitPrefix(prefix: String) {
        gitPrefix = prefix
    }
}
