plugins {
    application
}

dependencies {
    implementation(project(":simulation-core"))
}

application {
    mainClass.set("com.kosmos.atlas.headless.HeadlessMain")
}
