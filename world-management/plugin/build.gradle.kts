repositories {
	mavenLocal()
}

dependencies {
	compileOnly(libs.monumenta.common)
	compileOnly(libs.log4j.core)
	compileOnly(libs.commandapi)
	compileOnly(libs.nbtapi)
	compileOnly(project(":network-relay"))
	compileOnly(project(":redis-sync:redissync"))
}

tasks {
	shadowJar {
  }
}
