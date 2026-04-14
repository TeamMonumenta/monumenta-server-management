repositories {
	mavenLocal()
}

dependencies {
	implementation(libs.lettuce)
	compileOnly(libs.log4j.core)
	compileOnly(libs.monumenta.common)
	compileOnly(project(":network-relay"))
	compileOnly(libs.commandapi)

	// velocity dependencies
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}

tasks {
	shadowJar {
		// Exclude all META-INF content from the shaded JAR.
		//
		// Lettuce (the Redis client) transitively depends on Netty, which Paper/Minecraft
		// already bundles. Shading Lettuce without exclusions would include:
		//   - META-INF/io.netty.versions.properties: Netty uses this to detect version
		//     conflicts at runtime; having two copies (ours + Paper's) causes warnings or failures.
		//   - META-INF/native/: Netty's native transport libraries (epoll, kqueue .so/.dll).
		//     These can't be safely relocated by shading and cause UnsatisfiedLinkError if
		//     two copies attempt to load.
		//   - META-INF/native-image/: GraalVM native-image configuration — irrelevant here.
		//   - META-INF/maven/: POM metadata from shaded deps — not needed at runtime.
		//
		// The service files (META-INF/services/) that get excluded are also safe to drop:
		//   - lettuce-core: javax.enterprise.inject.spi.Extension (CDI/Jakarta EE bean
		//     injection hook — not applicable in a Minecraft plugin environment)
		//   - netty-common: reactor.blockhound.integration.BlockHoundIntegration (dev tool
		//     for detecting blocking calls in async code; only activates if a BlockHound
		//     agent is present, which never happens in production)
		//
		// A narrower exclusion (e.g. only .SF/.DSA/.RSA signing files) would be insufficient
		// because of the native lib and version-properties conflicts above.
		exclude("META-INF/**/*")
	}
}
