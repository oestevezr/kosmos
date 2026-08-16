plugins {
    java
}

allprojects {
    group = "com.kosmos.atlas"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-parameters"))
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxHeapSize = "1g"
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    dependencies {
        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        add("testImplementation", libs.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    }
}

/**
 * simulation-core is the deterministic, headless heart of the game (see spec §35, §37).
 * It must never depend on libGDX, LWJGL, Android APIs or any rendering/audio framework.
 * This task fails the build the moment a forbidden dependency sneaks onto its compile
 * classpath, so the isolation rule is enforced mechanically rather than by convention.
 */
val forbiddenCoreDependencyPatterns = listOf(
    "badlogicgames", "lwjgl", "android", "libgdx"
)

tasks.register("checkCoreIsolation") {
    group = "verification"
    description = "Fails if simulation-core depends on any rendering/platform framework."
    doLast {
        val core = project(":simulation-core")
        val offending = core.configurations
            .findByName("compileClasspath")
            ?.resolvedConfiguration
            ?.resolvedArtifacts
            ?.map { it.moduleVersion.id.toString() }
            ?.filter { id -> forbiddenCoreDependencyPatterns.any { id.contains(it, ignoreCase = true) } }
            ?: emptyList()

        if (offending.isNotEmpty()) {
            throw GradleException(
                "simulation-core must stay a pure-Java module (spec §37). " +
                    "Forbidden dependencies found: $offending"
            )
        }
        println("simulation-core isolation OK: no rendering/platform dependencies found.")
    }
}

tasks.named("build") {
    dependsOn("checkCoreIsolation")
}
