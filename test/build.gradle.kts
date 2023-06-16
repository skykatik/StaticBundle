dependencies {
    implementation(project(":core"))
    annotationProcessor(project(":processor"))
    compileOnly(project(":processor"))
}
