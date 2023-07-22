import java.util.*

plugins {
    val isJitpack = System.getenv("JITPACK") == "true"

    id("${if (isJitpack) "com" else "io"}.github.skykatik.staticbundle")
}

sourceSets {
    create("another")
}

val anotherImplementation: Configuration = configurations["anotherImplementation"]

dependencies {
    implementation(project(":core"))
    anotherImplementation(project(":core"))
}

staticBundle {
    sourceSetSettings.create("main") {
        resourceFilenameFormat.set("messages{locale}.properties")
        messageSourceClassName.set("io.github.skykatik.staticbundle.test.CustomMessageSource")

        settings {
            setting {
                locale.set(Locale.ROOT)
                pluralForms.set(4)
                pluralFunction.set("value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2")
            }

            setting {
                locale.set(Locale.ENGLISH)
                pluralForms.set(2)
                pluralFunction.set("value == 1 ? 0 : 1")
            }
        }
    }

    sourceSetSettings.create("another") {
        resourceFilenameFormat.set("messages{locale}.properties")
        messageSourceClassName.set("test.SuperMsgSource")

        settings {
            setting {
                locale.set(Locale.ROOT)
                pluralForms.set(4)
                pluralFunction.set("value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2")
            }

            setting {
                locale.set(Locale.ENGLISH)
                pluralForms.set(2)
                pluralFunction.set("value == 1 ? 0 : 1")
            }
        }
    }
}
