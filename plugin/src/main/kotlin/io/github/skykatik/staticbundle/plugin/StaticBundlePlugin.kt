package io.github.skykatik.staticbundle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

class StaticBundlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.apply<JavaPlugin>()

        val ext = project.extensions.create(
            StaticBundleExtension::class.java,
            "staticBundle", DefaultStaticBundleExtension::class.java, project.objects
        )

        val javaExt = project.extensions.getByType<JavaPluginExtension>()

        ext.sourceSetSettings.all {
            javaExt.sourceSets.named(name) {
                naming.convention(project.provider { PropertyNaming.instance() })

                val sourceSetName = getTaskName("staticBundle", "Processor")
                val task = project.tasks.register<StaticBundleProcessor>(sourceSetName, this, this@all)

                task.configure {
                    java.srcDir(codegenDir)
                }

                project.tasks.named(compileJavaTaskName) {
                    dependsOn(task)
                }
            }
        }
    }
}
