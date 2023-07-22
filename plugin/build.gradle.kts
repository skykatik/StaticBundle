import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
}

version = "0.1.0-SNAPSHOT"
group = "io.github.skykatik.staticbundle"

val isJitpack = System.getenv("JITPACK") == "true"

gradlePlugin {

    plugins {
        register("codegen") {
            if (isJitpack)
                id = "io.github.skykatik.staticbundle"
            else
                id = "com.github.skykatik.staticbundle"
            displayName = "Static Bundle Generator"
            description = "A gradle plugin for generating java classes from .proprerties files."
            implementationClass = "io.github.skykatik.staticbundle.plugin.StaticBundlePlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
}

tasks.withType<JavaCompile> {
    options.javaModuleVersion.set(rootProject.version.toString())
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}
