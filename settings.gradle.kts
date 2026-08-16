plugins {
    // Lets Gradle auto-discover (or auto-download) a JDK 17 toolchain on any machine, instead of
    // requiring every contributor/CI runner to manually export JAVA_HOME to a keg-only Homebrew
    // install. See root build.gradle.kts's `languageVersion.set(JavaLanguageVersion.of(17))`.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "atlas-city"

include(
    "simulation-core",
    "game-client",
    "platform-desktop",
    "headless-runner",
    "benchmark"
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://oss.sonatype.org/content/repositories/snapshots/") { mavenContent { snapshotsOnly() } }
    }
}
