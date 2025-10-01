dependencies {
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
	implementation(project(":redis-sync:common"))
}
