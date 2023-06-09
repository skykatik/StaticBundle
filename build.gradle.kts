plugins {
    `java-library`
}

version = "1.0.0-SNAPSHOT"
group = "com.github.skykatik"

repositories {
    mavenCentral()
}

dependencies {

}

tasks.withType<JavaCompile> {
    options.javaModuleVersion.set(version.toString())
    options.encoding = "UTF-8"
    options.release.set(17)
}
