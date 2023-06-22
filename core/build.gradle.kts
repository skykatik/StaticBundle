plugins {
    `maven-publish`
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = group.toString()
            artifactId = "core"

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("Static Bundle Core")
                description.set("Base API for precompiled bundles.")
                url.set("https://github.com/skykatik/StaticBundle")
                inceptionYear.set("2023")

                developers {
                    developer { name.set("Skat") }
                }

                licenses {
                    license {
                        name.set("LGPL-3.0")
                        url.set("https://github.com/skykatik/StaticBundle/LICENSE")
                        distribution.set("repo")
                    }
                }

                scm {
                    url.set("https://github.com/skykatik/StaticBundle")
                    connection.set("scm:git:git://github.com/skykatik/StaticBundle.git")
                    developerConnection.set("scm:git:ssh://git@github.com:skykatik/StaticBundle.git")
                }
            }
        }
    }
}
