package com.playmonumenta.gradleconfig

import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import org.gradle.api.Project

interface MonumentaExtension {
    /**
     * Specifies the URL of the snapshot repo (for publishing).
     * The snapshot repo will be used for all non-tagged commits, and will be versioned with `-SNAPSHOT` appended.
     *
     * @param url The url of the snapshot repo.
     * @see MonumentaExtension.releasesRepo
     */
    fun snapshotRepo(url: String)

    /**
     * Specifies the URL of the releases repo (for publishing).
     * The release repo will be used for all tagged commits, and will be versioned as the tag name.
     *
     * @param url The url of the release repo.
     * @see MonumentaExtension.snapshotRepo
     */
    fun releasesRepo(url: String)

    /**
     * Specifies the plugin id of the plugin.
     *
     * @param id the "plugin" name of the plugin, used in some places like bukkit configuration.
     */
    fun id(id: String)

    /**
     * Specifies the name of the plugin.
     *
     * @param name The name of the plugin, used in some places like deploy.
     */
    fun name(name: String)

    /**
     * Disables maven publishing for this project.
     */
    fun disableMaven()

    /**
     * Disables javadoc for this project.
     */
    fun disableJavadoc()

    /**
     * Specifies the project that contains the actual plugin.
     * This defaults to the root project, and is useful in cases with version adapters and such.
     *
     * @param path The path of the subproject, like `:plugins`.
     */
    fun pluginProject(path: String, config: Project.() -> Unit = {})

    /**
     * Builds the plugin project as a paper project, automagically generating yml files.
     *
     * @param main The main class.
     * @param order The load order.
     * @param apiVersion The version of paper api to use. Don't include `R0.1-SNAPSHOT`. This should look like an MC version.
     * @param authors The list of authors. Defaults to `"Team Monumenta"`
     * @param depends The list of dependencies that are required for the plugin to start.
     * @param softDepends The list of optional dependencies.
     * @param apiJarVersion The actual version of the paper API jar. Used for weird edgecases.
     */
    fun paper(
        main: String, order: BukkitPluginDescription.PluginLoadOrder, apiVersion: String,
        authors: List<String> = listOf("Team Monumenta"),
        depends: List<String> = listOf(),
        softDepends: List<String> = listOf(),
        apiJarVersion: String = "$apiVersion-R0.1-SNAPSHOT",
        action: BukkitPluginDescription.() -> Unit = {}
    )

    /**
     * Builds the plugin project as a bungee/waterfall project, automagically generating yml files.
     *
     * @param main The main class.
     * @param apiVersion The version of waterfall's API to use.
     * @param authors The list of authors. Defaults to `"Team Monumenta"`
     * @param depends The list of dependencies that are required for the plugin to start.
     * @param softDepends The list of optional dependencies.
     */
    fun waterfall(
        main: String,
        apiVersion: String,
        authors: List<String> = listOf("Team Monumenta"),
        depends: List<String> = listOf(),
        softDepends: List<String> = listOf()
    )

    fun versionAdapterApi(name: String, paper: String? = null, config: Project.() -> Unit = {})
    fun versionAdapterUnsupported(name: String, config: Project.() -> Unit = {})
    fun versionAdapter(name: String, devBundle: String, config: Project.() -> Unit = {})
    fun javaSimple(name: String, config: Project.() -> Unit = {})

    fun publishingCredentials(name: String, token: String)

    fun gitPrefix(prefix: String)
}
