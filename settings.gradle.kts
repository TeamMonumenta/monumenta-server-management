rootProject.name = "monumenta-management"

pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.playmonumenta.com/releases/")
	}
}

include("network-chat")
include("network-relay")
include("redis-sync")
include("structure-management")
include("world-management")
include("redis-sync")

include("redis-sync:adapter_api")
include("redis-sync:adapter_v1_20_R3")
include(":redis-sync:redissync-example")
include(":redis-sync:redissync")
project(":redis-sync:redissync-example").projectDir = file("redis-sync/example")
project(":redis-sync:redissync").projectDir = file("redis-sync/plugin")

includeBuild("gradle-plugin")
