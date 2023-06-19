package io.github.skykatik.staticbundle.plugin

import org.gradle.api.provider.Property
import java.util.*

interface LocaleSettings {

    val locale: Property<Locale>

    val pluralForms: Property<Int>

    val pluralFunction: Property<String>
}
