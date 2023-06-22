version = "0.1.0-SNAPSHOT"
group = "io.github.skykatik.staticbundle"

subprojects {

    version = rootProject.version
    group = rootProject.group

    apply<JavaPlugin>()

    tasks.withType<JavaCompile>().configureEach {
        options.javaModuleVersion.set(version.toString())
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        archiveBaseName.set("staticbundle")
        archiveAppendix.set(project.name)
        archiveVersion.set(version.toString())
    }
}
