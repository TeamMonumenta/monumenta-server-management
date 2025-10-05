plugins {
	`java-library`
}

group = "com.floweytf"
version = "1.0.0"

repositories {
	mavenCentral()
}

dependencies {
	api("org.jetbrains:annotations:25.0.0")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}
