dependencies {
    implementation(project(":simulation-core"))
    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    implementation(libs.findLibrary("gdx-core").get())
}
