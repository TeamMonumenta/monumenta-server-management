repositories {
	mavenLocal()
}

dependencies {
	implementation(libs.lettuce)
	compileOnly(libs.log4j.core)
	compileOnly(libs.monumenta.common)
	compileOnly(project(":network-relay"))
	compileOnly(libs.commandapi)
	compileOnly(libs.brigadier)

	// velocity dependencies
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)

	/*
	 * Test deps. paper-api and commandapi are compileOnly above, so the test source set must
	 * name them itself. network-relay + commandapi are needed only because MonumentaRedisSyncAPI's
	 * static init touches them.
	 */
	testImplementation(libs.paperapi.test)
	testImplementation(libs.mockbukkit)
	testImplementation(libs.embedded.redis)
	testImplementation(libs.monumenta.common)
	testImplementation(libs.log4j.core)
	testImplementation(libs.commandapi)
	testImplementation(project(":network-relay"))
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
		//   - META-INF/native-image/: GraalVM native-image configuration; irrelevant here.
		//   - META-INF/maven/: POM metadata from shaded deps; not needed at runtime.
		//
		// The service files (META-INF/services/) that get excluded are also safe to drop:
		//   - lettuce-core: javax.enterprise.inject.spi.Extension (CDI/Jakarta EE bean
		//     injection hook, not applicable in a Minecraft plugin environment)
		//   - netty-common: reactor.blockhound.integration.BlockHoundIntegration (dev tool
		//     for detecting blocking calls in async code; only activates if a BlockHound
		//     agent is present, which never happens in production)
		//
		// A narrower exclusion (e.g. only .SF/.DSA/.RSA signing files) would be insufficient
		// because of the native lib and version-properties conflicts above.
		exclude("META-INF/**/*")
	}
}

testing {
	suites {
		val test by getting(JvmTestSuite::class) {
			useJUnitJupiter(libs.versions.junit.get())
		}
	}
}

/*
 * These are integration tests: they exec a bundled redis binary (glibc host) and bind an
 * ephemeral port. They run under the standard `test` task on purpose: a separate target is
 * one more thing to remember, and `./gradlew test` is where people look.
 *
 * MockBukkit reports an unimplemented API as a *skipped* test rather than a failure
 * (UnimplementedOperationException extends JUnit's AssumptionException), which would let a
 * silently untested path pass as green. Set -PfailOnSkippedTests to turn that into a failure;
 * CI should always set it.
 */
/* LifecycleFuzzTest knobs: -Pmrs.fuzz.seeds=200 runs more seeds, -Pmrs.fuzz.seed=N reruns one */
tasks.named<Test>("test") {
	listOf("mrs.fuzz.seeds", "mrs.fuzz.seed").forEach { key ->
		project.findProperty(key)?.let { systemProperty(key, it.toString()) }
	}
}

if (project.hasProperty("failOnSkippedTests")) {
	tasks.named<Test>("test") {
		doLast {
			val resultDir = reports.junitXml.outputLocation.get().asFile
			val skipped = resultDir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty()
				.sumOf { file ->
					Regex("""skipped="(\d+)"""").find(file.readText())?.groupValues?.get(1)?.toInt() ?: 0
				}
			if (skipped > 0) {
				throw GradleException(
					"$skipped test(s) were skipped and -PfailOnSkippedTests is set. " +
						"A MockBukkit UnimplementedOperationException reports as a skip, not a failure."
				)
			}
		}
	}
}
