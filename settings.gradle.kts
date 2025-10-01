rootProject.name = "monumenta-management"

pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.playmonumenta.com/releases/")
		mavenLocal()
	}
}

include("network-chat")
include("network-relay")
include("structure-management")
include("world-management")

include("redis-sync")
include("redis-sync:adapter_v1_20_R3")
include("redis-sync:plugin")
include("redis-sync:common")
include("redis-sync:velocity")

include("limbo")
include("examples:redis-sync")
