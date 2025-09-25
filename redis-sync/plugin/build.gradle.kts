repositories {
	mavenLocal()
}

dependencies {
	compileOnly(project(":network-relay"))
	compileOnly(libs.commandapi)
	implementation(project(":redis-sync:core"))
	annotationProcessor("com.floweytf.coro:ap:0.0.1-SNAPSHOT")
	implementation("com.floweytf.coro:coro:0.0.1-SNAPSHOT")
}

tasks {
	shadowJar {
		exclude("META-INF/**/*")
		relocate("com.floweytf.coro", "com.playmonumenta.redissync.shadow.coro")
	}
}
