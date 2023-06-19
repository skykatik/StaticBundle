package io.github.skykatik.staticbundle.plugin

import org.gradle.api.Action

interface LocaleSettingsSpec {

    fun setting(action: Action<in LocaleSettings>)
}
