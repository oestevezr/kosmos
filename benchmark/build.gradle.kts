// Minimal JMH setup without the community Gradle plugin (keeps the build hermetic —
// see spec §36.3: do not add tooling to the hot path without profiling justification).
// Benchmarks are compiled from src/jmh and launched via `runJmh`.

val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

sourceSets {
    create("jmh") {
        java.srcDir("src/jmh/java")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val jmhImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    implementation(project(":simulation-core"))
    jmhImplementation(libs.findLibrary("jmh-core").get())
    jmhImplementation(libs.findLibrary("jmh-annprocess").get())
    "jmhAnnotationProcessor"(libs.findLibrary("jmh-annprocess").get())
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Runs JMH microbenchmarks (spec §36.3, §49)."
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets["jmh"].runtimeClasspath
    dependsOn("jmhClasses")
}
