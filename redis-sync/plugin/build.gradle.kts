dependencies {
	implementation(libs.lettuce)
	compileOnly(libs.networkrelay)
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
