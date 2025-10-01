package com.playmonumenta.gradleconfig.ssh

import org.gradle.api.tasks.Input

class AllowAnyHosts private constructor() {
    companion object {
        val instance = AllowAnyHosts()
    }
}

class RemoteConfig(private val name: String) {
    @Input
    lateinit var host: String

    @Input
    var port: Int = 22

    @Input
    var timeout: Int = 0

    @Input
    var user: String? = null

    @Input
    lateinit var knownHosts: Any

    @Input
    lateinit var auth: Array<AuthProvider>

    override fun toString(): String {
        if (user != null)
            return "$name[$user@$host:$port]"
        return "$name[$host:$port]"
    }
}
