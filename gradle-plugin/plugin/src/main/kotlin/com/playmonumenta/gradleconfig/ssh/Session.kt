package com.playmonumenta.gradleconfig.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.playmonumenta.gradleconfig.MonumentaGradlePlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.*

class SessionHandler(private val remote: RemoteConfig) {
    private val jsch = JSch()
    private val session: Session

    private fun resolveHostKey(knownHosts: Any) {
        when (knownHosts) {
            is File -> {
                TODO("implement File")
            }

            is AllowAnyHosts -> {
                session.setConfig("StrictHostKeyChecking", "no")
                MonumentaGradlePlugin.LOGGER.warn("Host key checking is off. It may be vulnerable to man-in-the-middle attacks.")
            }

            else -> throw IllegalArgumentException("knownHosts must be file, collection of files, or allowAnyHosts")
        }
    }

    init {
        MonumentaGradlePlugin.LOGGER.info("Running version " + javaClass.`package`.implementationVersion)
        MonumentaGradlePlugin.LOGGER.info("JSCH version " + JSch.VERSION + ", expected 0.2.17")
        MonumentaGradlePlugin.LOGGER.info("Attempting to connect to remote $remote")
        session = jsch.getSession(remote.user, remote.host, remote.port)

        resolveHostKey(remote.knownHosts)

        MonumentaGradlePlugin.LOGGER.info("Using the following authentication attempts:")
        var found = false

        for (auth in remote.auth) {
            var res: Optional<String>

            try {
                res = auth.tryProvider(jsch)
            } catch (e: Exception) {
                MonumentaGradlePlugin.LOGGER.info("FAIL - ${auth.name}")
                MonumentaGradlePlugin.LOGGER.debug("Exception: ", e)
                continue
            }

            if (res.isEmpty) {
                MonumentaGradlePlugin.LOGGER.info("USING - ${auth.name}")
                found = true
                if (!auth.shouldContinue) {
                    break
                }
            } else {
                MonumentaGradlePlugin.LOGGER.info("SKIP - ${auth.name}: ${res.get()}")
            }
        }

        if (!found) {
            throw RuntimeException("Exhausted authentication methods, check logs!")
        }

        session.connect(remote.timeout)
    }

    fun execute(commandLine: String): Triple<Int, String, String> {
        MonumentaGradlePlugin.LOGGER.warn("Executing command '$commandLine' on $remote")
        val chan = session.openChannel("exec") as ChannelExec
        chan.setCommand(commandLine)

        val outputBuffer = ByteArrayOutputStream()
        val errorBuffer = ByteArrayOutputStream()

        val inputStream: InputStream = chan.getInputStream()
        val outputStream: InputStream = chan.getExtInputStream()

        chan.connect(remote.timeout)

        val tmp = ByteArray(1024)
        val returnCode: Int

        while (true) {
            while (inputStream.available() > 0) {
                val i = inputStream.read(tmp, 0, 1024)
                if (i < 0) break
                outputBuffer.write(tmp, 0, i)
            }
            while (outputStream.available() > 0) {
                val i = outputStream.read(tmp, 0, 1024)
                if (i < 0) break
                errorBuffer.write(tmp, 0, i)
            }

            if (chan.isClosed) {
                if ((inputStream.available() > 0) || (outputStream.available() > 0))
                    continue

                returnCode = chan.exitStatus
                break
            }
            try {
                Thread.sleep(100)
            } catch (_: java.lang.Exception) {
            }
        }

        return Triple(returnCode, outputBuffer.toString(), errorBuffer.toString());
    }

    fun put(from: File, to: String) {
        MonumentaGradlePlugin.LOGGER.info("Copying '$from' -> '$to' on $remote")
        val chan = session.openChannel("sftp") as ChannelSftp
        chan.connect()
        chan.put(from.path, to)
    }
}
