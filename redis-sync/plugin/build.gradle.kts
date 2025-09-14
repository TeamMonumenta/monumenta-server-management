dependencies {
	implementation(libs.lettuce)
	compileOnly(project(":network-relay"))
	compileOnly(libs.commandapi)

	// velocity dependencies
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}

tasks {
	shadowJar {
		exclude("META-INF/**/*")
	}
}
