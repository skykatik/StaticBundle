package io.github.skykatik.staticbundle.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

interface StaticBundleExtension {

    val sourceSetSettings: SourceSetSettingsContainer
}

abstract class DefaultStaticBundleExtension
@Inject constructor(objectFactory: ObjectFactory)
    : StaticBundleExtension {

    override val sourceSetSettings: SourceSetSettingsContainer =
        objectFactory.newInstance<DefaultSourceSetSettingsContainer>()
}
