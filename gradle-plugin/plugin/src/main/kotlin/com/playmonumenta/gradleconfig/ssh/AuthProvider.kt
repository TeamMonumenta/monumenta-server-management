package com.playmonumenta.gradleconfig.ssh

import com.jcraft.jsch.AgentIdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.PageantConnector
import com.jcraft.jsch.SSHAgentConnector
import com.playmonumenta.gradleconfig.MonumentaGradlePlugin
import java.io.File
import java.util.*

abstract class AuthProvider(val name: String, val shouldContinue: Boolean = false) {
    abstract fun tryProvider(jsch: JSch): Optional<String>
}

abstract class FileKeyProvider(name: String, shouldContinue: Boolean = false) : AuthProvider(name, shouldContinue) {
    abstract fun getIdentityFile(): String?

    override fun tryProvider(jsch: JSch): Optional<String> {
        val identityPath = getIdentityFile()
        val identityFilePassword = System.getenv("IDENTITY_FILE_PASSWORD") ?: ""

        if (identityPath == null)
            return Optional.of("no path specified")

        val identityFile = File(identityPath)

        if (!identityFile.exists()) {
            return Optional.of("cannot find identity file at '$identityPath'")
        }

        try {
            jsch.addIdentity(identityPath, identityFilePassword)
        } catch (e: Exception) {
            MonumentaGradlePlugin.LOGGER.debug("Failed to load key: ", e)
            return Optional.of("failed to parse identity '$identityPath': ${e.message}")
        }

        return Optional.empty()
    }
}

class EnvKeyProvider : FileKeyProvider("IdentityFileEnv") {
    override fun getIdentityFile(): String? {
        return System.getenv("IDENTITY_FILE")
    }
}

class OpenSSHProvider(private val keyName: String) : FileKeyProvider("\$HOME/.ssh/$keyName", true) {
    override fun getIdentityFile(): String? {
        return File(System.getProperty("user.home")).resolve(".ssh").resolve(keyName).path
    }
}

class PageantKeyProvider : AuthProvider("Pageant") {
    override fun tryProvider(jsch: JSch): Optional<String> {
        if (!System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
            return Optional.of("pageant can only be used on windows")
        }

        try {
            jsch.identityRepository = AgentIdentityRepository(PageantConnector())
            return Optional.empty()
        } catch (e: Exception) {
            MonumentaGradlePlugin.LOGGER.debug("Failed to load Pageant: ", e)
            return Optional.of("failed to use PageantConnector: ${e.message}")
        }
    }
}

class SSHAgentKeyProvider : AuthProvider("SSHAgent") {
    override fun tryProvider(jsch: JSch): Optional<String> {
        if (System.getenv("SSH_AUTH_SOCK") == null) {
            return Optional.of("missing env variable SSH_AUTH_SOCK")
        }

        try {
            jsch.identityRepository = AgentIdentityRepository(SSHAgentConnector())
            return Optional.empty()
        } catch (e: Exception) {
            MonumentaGradlePlugin.LOGGER.debug("Failed to use SSHAgentConnector: ", e)
            return Optional.of("failed to use SSHAgentConnector: ${e.message}")
        }
    }
}
