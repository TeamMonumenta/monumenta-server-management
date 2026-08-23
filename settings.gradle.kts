rootProject.name = "monumenta-management"

pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.playmonumenta.com/releases/")
	}
}

include("monumenta-common")
include("network-chat")
include("network-relay")
include("redis-sync")
include("structure-management")
include("world-management")

include("redis-sync:adapter_api")
include("redis-sync:adapter_v1_20_R3")
include(":redis-sync:redissync-example")
include(":redis-sync:redissync")
project(":redis-sync:redissync-example").projectDir = file("redis-sync/example")
project(":redis-sync:redissync").projectDir = file("redis-sync/plugin")

include("world-management:adapter_api")
include("world-management:adapter_unsupported")
include("world-management:adapter_v1_20_R3")
include(":world-management:worldmanagement")
project(":world-management:worldmanagement").projectDir = file("world-management/plugin")

includeBuild("gradle-plugin")
