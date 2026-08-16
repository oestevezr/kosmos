plugins {
    application
}

val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(project(":simulation-core"))
    implementation(project(":game-client"))
    implementation(libs.findLibrary("gdx-backend-lwjgl3").get())
    implementation(variantOf(libs.findLibrary("gdx-platform").get()) { classifier("natives-desktop") })
}

application {
    mainClass.set("com.kosmos.atlas.desktop.DesktopLauncher")
}

tasks.named<JavaExec>("run") {
    // libGDX/LWJGL3 windowing requires running on the first thread on macOS.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}
