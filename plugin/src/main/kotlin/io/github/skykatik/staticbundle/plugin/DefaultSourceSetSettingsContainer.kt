package io.github.skykatik.staticbundle.plugin

import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.typeOf
import javax.inject.Inject

abstract class DefaultSourceSetSettingsContainer @Inject constructor(
    private val objectFactory: ObjectFactory,
    instantiator: Instantiator,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : AbstractValidatingNamedDomainObjectContainer<SourceSetSettings>(
    SourceSetSettings::class.java,
    instantiator,
    collectionCallbackActionDecorator
),
    SourceSetSettingsContainer {

    override fun doCreate(name: String): SourceSetSettings {
        return objectFactory.newInstance<DefaultSourceSetSettings>(name)
    }

    override fun getPublicType(): TypeOf<*> {
        return typeOf<SourceSetSettingsContainer>()
    }
}
