plugins {
	java
}

dependencies {
	compileOnly(project(":redis-sync:plugin"))
}

group = "com.playmonumenta"
description = "redissync-example"
version = rootProject.version
