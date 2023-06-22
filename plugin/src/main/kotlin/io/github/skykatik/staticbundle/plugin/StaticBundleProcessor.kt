package io.github.skykatik.staticbundle.plugin

import io.github.skykatik.staticbundle.gen.StaticBundleProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.NormalizeLineEndings
import javax.inject.Inject

@DisableCachingByDefault(because = "Not worth caching")
abstract class StaticBundleProcessor @Inject constructor(
    sourceSet: SourceSet,
    private val sett: DefaultSourceSetSettings
) : DefaultTask() {

    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val resourceDir: ConfigurableFileCollection = project.files(sourceSet.resources)

    @get:OutputDirectory
    abstract val codegenDir: DirectoryProperty

    init {
        codegenDir.convention(project.layout.buildDirectory.dir("generated/sources/codegen/java/${sourceSet.name}"))
    }

    @TaskAction
    fun run() {
        val codegenDir = codegenDir.get()
        val gen = StaticBundleProcessor(
            project,
            codegenDir,
            resourceDir,
            sett
        )
        gen.validate()
        gen.generate()
    }
}
