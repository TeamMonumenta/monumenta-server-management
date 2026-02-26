# com.playmonumenta.gradle-config

A Gradle plugin that provides standardized build configuration for all Monumenta ecosystem projects. When applied to a project it configures:

- **Checkstyle** (with a shared ruleset and optional per-project suppression file)
- **PMD** (PMD 7.x ruleset; violations are warnings, not errors)
- **Error Prone + NullAway** (errors promoted to warnings; several checks disabled to match Monumenta conventions)
- **Java 21** source/target compatibility and UTF-8 encoding throughout
- **Git-based versioning** via the git-version plugin
- **Maven publishing** (sources + javadoc JARs, credentials via environment variables)
- **SSH deployment helpers** for the Monumenta server fleet

---

## Building

```bash
./gradlew :plugin:build
```

The built JAR is produced at `plugin/build/libs/`.

---

## Publishing (Official)

Publishing to the Monumenta Maven repository requires credentials:

```bash
export USERNAME=<maven-username>
export TOKEN=<maven-token>
./gradlew :plugin:publish
```

- Tagged commits publish to `https://maven.playmonumenta.com/releases`
- Untagged commits publish to `https://maven.playmonumenta.com/snapshots`

The version is determined automatically from the nearest git tag via the git-version plugin.

---

## Local Testing (without publishing)

### Option 1 - Using Gradle Composite Build
Add the below line temporarily to `settings.gradle.kts`. Make sure you use the correct path for your system:
```diff
pluginManagement {
+   includeBuild("../../monumenta-server-management/gradle-plugin")
   repositories {
       gradlePluginPortal()
       maven("https://repo.papermc.io/repository/maven-public/")
       maven("https://maven.playmonumenta.com/releases")
   }
}
```

Now all you have to do is build your project normally. Gradle will follow this path and build the gradle-plugin automatically.

### Option 2 - Maven Local

You can publish to your local Maven cache instead:

```bash
# In the gradle-plugin directory:
./gradlew :plugin:publishToMavenLocal
```

Then add `mavenLocal()` as the **first** entry in `pluginManagement.repositories` of the consuming project. Repeat the publish step whenever you change the plugin.

---

## Usage in a Consuming Project

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.playmonumenta.com/releases")
    }
}
```

### `build.gradle.kts`

```kotlin
plugins {
    id("com.playmonumenta.gradle-config") version "3.+"
}

monumenta {
    id("MyPlugin")
    name("MyPlugin")
    pluginProject(":MyPlugin")
    paper("com.example.MyPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20")
}
```

### Per-project Checkstyle suppressions

Place a standard Checkstyle suppression file at `config/checkstyle/suppressions.xml` relative to the project root. It will be loaded automatically with `optional="true"` — projects without this file are unaffected.

Example `suppressions.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">

<suppressions>
    <suppress files=".*[\\/]SomeGeneratedFile\.java"
              checks=".*"/>
</suppressions>
```

### PMD violations as errors

By default PMD violations are warnings and do not fail the build. To promote them to errors (failing the build), call `pmdWarningsAsErrors()` in the `monumenta` block:

```kotlin
monumenta {
    id("MyPlugin")
    name("MyPlugin")
    pmdWarningsAsErrors()
    ...
}
```

### Per-location PMD suppressions

Suppress individual PMD violations in code regardless of whether warnings-as-errors is enabled:

```java
@SuppressWarnings("PMD.SomeRuleName")
public void someMethod() { ... }
```
