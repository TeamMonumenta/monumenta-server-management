rootProject.name = "monumenta-management"

pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.playmonumenta.com/releases/")
	}
}

include("network-chat")
include("network-relay")
include("structure-management")
include("world-management")

include("redis-sync")
include("redis-sync:adapter_api")
include("redis-sync:adapter_v1_20_R3")
include("redis-sync:plugin")
include("redis-sync:core")
include("redis-sync:velocity")

include("limbo")
include("examples:redis-sync")
