package io.github.skykatik.staticbundle.plugin

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.provider.Property
import java.util.function.Function

interface SourceSetSettings : Named {

    val messageSourceClassName: Property<String>

    val resourceFilenameFormat: Property<String>

    val naming: Property<PropertyNaming>

    val contentTransformer: Property<Function<String, String>>

    fun settings(action: Action<in LocaleSettingsSpec>)
}
