package com.playmonumenta.gradleconfig.ssh

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.nio.charset.StandardCharsets

private fun getUsername(): String {
    val envVar = System.getenv("LOCKOUT_USERNAME")

    if (envVar != null)
        return envVar

    val git = ProcessBuilder("git", "config", "user.name")
        .redirectErrorStream(true)
        .start()
    git.waitFor()
    return git.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).lowercase().trim()
}

fun attemptLockout(session: SessionHandler, domain: String, shard: String, time: Int) {
    val result =
        session.execute("~/4_SHARED/lockouts/lockout '$domain' claim '$shard' \"${getUsername()}\" $time \"Automatic lockout (deploy script)\"")
    if (result.first != 0) {
        throw RuntimeException("Failed to deploy! Shard is currently being used by another developer!")
    }
}

fun checkLockout(session: SessionHandler, domain: String, shard: String) {
    val result = session.execute("~/4_SHARED/lockouts/lockout '$domain' check '$shard'")
    if (result.first != 0) {
        throw RuntimeException("Failed to deploy! Shard is currently being used by another developer!")
    }
}

fun easyCreateRemote(name: String, p: Int): RemoteConfig {
    return RemoteConfig(name).also {
        it.host = "admin-eu.playmonumenta.com"
        it.port = p
        it.user = "epic"
        it.knownHosts = AllowAnyHosts.instance
        it.auth = arrayOf(
            EnvKeyProvider(),
            SSHAgentKeyProvider(),
            OpenSSHProvider("id_ed25519"),
            OpenSSHProvider("id_rsa"),
            PageantKeyProvider()
        )
    }
}

class ShardLockInfo(
    private val domain: String,
    private val shard: String,
    private val defaultTime: Int,
    private val checkOnly: Boolean = false
) {
    fun doLock(session: SessionHandler) {
        if (checkOnly) {
            checkLockout(session, domain, shard)
        } else {
            attemptLockout(session, domain, shard, defaultTime)
        }
    }
}

fun easyConfigureDeployTask(
    proj: Project,
    dependsOn: Task,
    name: String,
    category: String,
    action: () -> Unit
) {
    proj.tasks.register(name) {
        it.group = category
        it.dependsOn(dependsOn)
        it.doLast {
            action()
        }
    }
}

fun easyCreateNormalDeploy(
    project: Project,
    dependsOn: Task,
    deployFile: Provider<RegularFile>,
    ssh: RemoteConfig,
    name: String,
    projectName: String,
    lockConfig: ShardLockInfo?,
    vararg paths: String
) {
    if (paths.isEmpty())
        throw IllegalArgumentException("paths must be non-empty")

    easyConfigureDeployTask(project, dependsOn, "$name-deploy-lock", "Deploy (locking)") {
        with(SessionHandler(ssh)) {
            lockConfig?.doLock(this)

            for (path in paths)
                execute("cd $path && rm -f ${projectName}-*.jar ${projectName}.jar")
            for (path in paths)
                put(deployFile.get().asFile, "${path}/${projectName}-${project.version}.jar")
        }
    }

    easyConfigureDeployTask(project, dependsOn, "$name-deploy", "Deploy") {
        with(SessionHandler(ssh)) {
            for (path in paths)
                execute("cd $path && rm -f ${projectName}-*.jar ${projectName}.jar")
            for (path in paths)
                put(deployFile.get().asFile, "${path}/${projectName}-${project.version}.jar")
        }
    }
}

fun easyCreateSymlinkDeploy(
    project: Project,
    dependsOn: Task,
    deployFile: Provider<RegularFile>,
    ssh: RemoteConfig,
    name: String,
    symlinkBaseName: String,
    lockConfig: ShardLockInfo?,
    vararg paths: String
) {
    if (paths.isEmpty())
        throw IllegalArgumentException("paths must be non-empty")

    easyConfigureDeployTask(project, dependsOn, "$name-deploy-lock", "Deploy (locking)") {
        with(SessionHandler(ssh)) {
            lockConfig?.doLock(this)

            val versionedFileName = deployFile.get().asFile.name
            for (path in paths)
                put(deployFile.get().asFile, path)
            for (path in paths)
                execute("cd $path && rm -f $symlinkBaseName.jar && ln -s $versionedFileName $symlinkBaseName.jar")
        }
    }

    easyConfigureDeployTask(project, dependsOn, "$name-deploy", "Deploy") {
        with(SessionHandler(ssh)) {
            val versionedFileName = deployFile.get().asFile.name
            for (path in paths)
                put(deployFile.get().asFile, path)
            for (path in paths)
                execute("cd $path && rm -f $symlinkBaseName.jar && ln -s $versionedFileName $symlinkBaseName.jar")
        }
    }
}

fun easySetup(project: Project, dependsOn: Task, deployFile: Provider<RegularFile>, projectName: String, serverConfigSubdir: String = "plugins") {
    val basicssh = easyCreateRemote("basicssh", 8822)
    val adminssh = easyCreateRemote("adminssh", 9922)

    for (i in 1..4) {
        easyCreateNormalDeploy(
            project,
            dependsOn,
            deployFile,
            basicssh,
            "dev$i",
            projectName,
            ShardLockInfo("build", "dev$i", 30),
            "/home/epic/dev${i}_shard_${serverConfigSubdir}"
        )
    }

    easyCreateNormalDeploy(
        project,
        dependsOn,
        deployFile,
        basicssh,
        "futurama",
        projectName,
        ShardLockInfo("build", "futurama", 30),
        "/home/epic/futurama_shard_${serverConfigSubdir}"
    )

    easyCreateNormalDeploy(
        project,
        dependsOn,
        deployFile,
        basicssh,
        "mob",
        projectName,
        ShardLockInfo("build", "mob", 30),
        "/home/epic/mob_shard_${serverConfigSubdir}"
    )

    easyCreateSymlinkDeploy(
        project,
        dependsOn,
        deployFile,
        basicssh,
        "stage",
        projectName,
        ShardLockInfo("stage", "*", 30),
        "/home/epic/stage/m17/server_config/${serverConfigSubdir}",
        "/home/epic/stage/m18/server_config/${serverConfigSubdir}"
    )

    easyCreateSymlinkDeploy(
        project,
        dependsOn,
        deployFile,
        basicssh,
        "volt",
        projectName,
        ShardLockInfo("volt", "*", 30),
        "/home/epic/volt/m17/server_config/${serverConfigSubdir}",
        "/home/epic/volt/m18/server_config/${serverConfigSubdir}"
    )

    easyCreateSymlinkDeploy(
        project,
        dependsOn,
        deployFile,
        adminssh,
        "m119",
        projectName,
        ShardLockInfo("build", "m119", 30),
        "/home/epic/project_epic/m119/${serverConfigSubdir}"
    )

    easyCreateSymlinkDeploy(
        project,
        dependsOn,
        deployFile,
        adminssh,
        "build",
        projectName,
        ShardLockInfo("build", "*", 0, true),
        "/home/epic/project_epic/server_config/${serverConfigSubdir}"
    )

    easyCreateSymlinkDeploy(
        project,
        dependsOn,
        deployFile,
        adminssh,
        "play",
        projectName,
        ShardLockInfo("play", "*", 0, true),
        "/home/epic/play/m17/server_config/${serverConfigSubdir}",
        "/home/epic/play/m18/server_config/${serverConfigSubdir}"
    )
}
