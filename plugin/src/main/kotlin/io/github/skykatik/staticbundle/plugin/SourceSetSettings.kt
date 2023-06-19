package io.github.skykatik.staticbundle.plugin

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.provider.Property

interface SourceSetSettings : Named {

    val messageSourceClassName: Property<String>

    val resourceFilenameFormat: Property<String>

    fun settings(action: Action<in LocaleSettingsSpec>)
}
