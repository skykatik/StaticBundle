plugins {
    `java-library`
}

version = "0.1.0-SNAPSHOT"
group = "com.github.skykatik"

subprojects {

    apply(plugin = "java-library")

    tasks.withType<JavaCompile> {
        options.javaModuleVersion.set(rootProject.version.toString())
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
