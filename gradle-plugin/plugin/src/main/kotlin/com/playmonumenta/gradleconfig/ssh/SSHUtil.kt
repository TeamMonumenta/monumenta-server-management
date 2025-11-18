package com.playmonumenta.gradleconfig.ssh

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import java.nio.charset.StandardCharsets

private fun getUsername(): String {
    val envVar = System.getenv("LOCKOUT_USERNAME")

    if (envVar != null)
        return envVar

    val git = Runtime.getRuntime().exec("git config user.name")
    git.waitFor()
    return git.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).lowercase().trim();
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
    shadowJarTask: Jar,
    name: String,
    category: String,
    action: () -> Unit
) {
    proj.tasks.create(name) {
        it.group = category
        it.dependsOn(shadowJarTask)
        it.doLast {
            action()
        }
    }
}

fun easyCreateNormalDeploy(
    project: Project,
    shadowJarTask: Jar,
    ssh: RemoteConfig,
    name: String,
    projectName: String,
    lockConfig: ShardLockInfo?,
    vararg paths: String
) {
    if (paths.isEmpty())
        throw IllegalArgumentException("paths must be non-empty")

    easyConfigureDeployTask(project, shadowJarTask, "$name-deploy-lock", "Deploy (locking)") {
        with(SessionHandler(ssh)) {
            lockConfig?.doLock(this)

            for (path in paths)
                execute("cd $path && rm -f ${projectName}*.jar")
            for (path in paths)
                put(shadowJarTask.archiveFile.get().asFile, "${path}/${projectName}-${project.version}.jar")
        }
    }

    easyConfigureDeployTask(project, shadowJarTask, "$name-deploy", "Deploy") {
        with(SessionHandler(ssh)) {
            for (path in paths)
                execute("cd $path && rm -f ${projectName}*.jar")
            for (path in paths)
                put(shadowJarTask.archiveFile.get().asFile, "${path}/${projectName}-${project.version}.jar")
        }
    }
}

fun easyCreateSymlinkDeploy(
    project: Project,
    shadowJarTask: Jar,
    ssh: RemoteConfig,
    name: String,
    fileName: String,
    lockConfig: ShardLockInfo?,
    vararg paths: String
) {
    if (paths.isEmpty())
        throw IllegalArgumentException("paths must be non-empty")

    easyConfigureDeployTask(project, shadowJarTask, "$name-deploy-lock", "Deploy (locking)") {
        with(SessionHandler(ssh)) {
            lockConfig?.doLock(this)

            for (path in paths)
                put(shadowJarTask.archiveFile.get().asFile, path)
            for (path in paths)
                execute("cd $path && rm -f $fileName.jar && ln -s ${shadowJarTask.archiveFileName.get()} $fileName.jar")
        }
    }

    easyConfigureDeployTask(project, shadowJarTask, "$name-deploy", "Deploy") {
        with(SessionHandler(ssh)) {
            for (path in paths)
                put(shadowJarTask.archiveFile.get().asFile, path)
            for (path in paths)
                execute("cd $path && rm -f $fileName.jar && ln -s ${shadowJarTask.archiveFileName.get()} $fileName.jar")
        }
    }
}

fun easySetup(project: Project, shadowJarTask: Jar) {
    val basicssh = easyCreateRemote("basicssh", 8822)
    val adminssh = easyCreateRemote("adminssh", 9922)
    val projectName = shadowJarTask.archiveBaseName.get()

    for (i in 1..4) {
        easyCreateNormalDeploy(
            project,
            shadowJarTask,
            basicssh,
            "dev$i",
            projectName,
            ShardLockInfo("build", "dev$i", 30),
            "/home/epic/dev${i}_shard_plugins"
        )
    }

    easyCreateNormalDeploy(
        project,
        shadowJarTask,
        basicssh,
        "futurama",
        projectName,
        ShardLockInfo("build", "futurama", 30),
        "/home/epic/futurama_shard_plugins"
    )

    easyCreateNormalDeploy(
        project,
        shadowJarTask,
        basicssh,
        "mob",
        projectName,
        ShardLockInfo("build", "mob", 30),
        "/home/epic/mob_shard_plugins"
    )

    easyCreateSymlinkDeploy(
        project,
        shadowJarTask,
        basicssh,
        "stage",
        projectName,
        ShardLockInfo("stage", "*", 30),
        "/home/epic/stage/m17/server_config/plugins",
        "/home/epic/stage/m18/server_config/plugins"
    )

    easyCreateSymlinkDeploy(
        project,
        shadowJarTask,
        basicssh,
        "volt",
        projectName,
        ShardLockInfo("volt", "*", 30),
        "/home/epic/volt/m17/server_config/plugins",
        "/home/epic/volt/m18/server_config/plugins"
    )

    easyCreateSymlinkDeploy(
        project,
        shadowJarTask,
        adminssh,
        "m119",
        projectName,
        ShardLockInfo("build", "m119", 30),
        "/home/epic/project_epic/m119/plugins"
    )

    easyCreateSymlinkDeploy(
        project,
        shadowJarTask,
        adminssh,
        "build",
        projectName,
        ShardLockInfo("build", "*", 0, true),
        "/home/epic/project_epic/server_config/plugins"
    )

    easyCreateSymlinkDeploy(
        project,
        shadowJarTask,
        adminssh,
        "play",
        projectName,
        ShardLockInfo("play", "*", 0, true),
        "/home/epic/play/m17/server_config/plugins",
        "/home/epic/play/m18/server_config/plugins"
    )
}
