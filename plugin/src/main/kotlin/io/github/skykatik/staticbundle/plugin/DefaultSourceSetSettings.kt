package io.github.skykatik.staticbundle.plugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class DefaultSourceSetSettings @Inject constructor(
    private val name: String,
    private val objectFactory: ObjectFactory
) : SourceSetSettings, LocaleSettingsSpec, LocaleSettings {

    val settings: MutableList<LocaleSettings> = ArrayList()

    override fun getName(): String {
        return name
    }

    override fun settings(action: Action<in LocaleSettingsSpec>) {
        action.execute(this)
    }

    override fun setting(action: Action<in LocaleSettings>) {
        configureAndAdd(settings, action)
    }

    private inline fun <reified T : Any> configureAndAdd(items: MutableList<T>, action: Action<in T>) {
        val item = objectFactory.newInstance<T>()
        action.execute(item)
        items.add(item)
    }
}
