version = "0.1.0-SNAPSHOT"
group = "io.github.skykatik"

subprojects {

    apply<JavaPlugin>()

    tasks.withType<JavaCompile> {
        options.javaModuleVersion.set(rootProject.version.toString())
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
