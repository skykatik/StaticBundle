## Static Bundle

A tiny processor for generating Java classes from specified `.properties` files.
The resulting classes contain accessor methods which return bundle string depending on locale.

### Installation

TBD...

_Currently only from maven local or jitpack_

### Gradle

Here `VERSION` it's short hash of commit, like a `9cbb53c65b5889f51b4a7c4e81d72907a6659d23`

In `build.gradle.kts`:
```kotlin
import java.util.Locale

plugins {
    // other plugins
    id("com.github.skykatik.staticbundle") version "VERSION"
}

repositories {
    // other repositories
    maven("https://www.jitpack.io")
}

dependencies {
    implementation("com.github.skykatik.StaticBundle:core:VERSION")
}

staticBundle {
  // Source set scoped settings 
  sourceSetSettings.create("main") {
    // Name format of .properties files. There {locale} is placeholder for toString() value of java.util.Locale 
    resourceFilenameFormat.set("messages{locale}.properties")
    // The full qualified name of generated class
    messageSourceClassName.set("your.packagename.GeneratedMessageSource")

    // List of supported locales
    settings {
      // Remember that the first defined bundle will be reference bundle
      setting {
        locale.set(Locale("ru", "RU"))
        pluralForms.set(4)
        // Raw code of plural form function. There `value` parameter is `long` and means amount
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
```

And in `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
```

### Format details

Processor requires at least one bundle, and
first locale in the list of supported will be _reference_ locale.
It means that you must specify indexes and names of arguments for
correct validation and generation.

The arguments in _reference_ locale are defined in the format: `{index:name:type}`,
where `index` is **zero-based** position of argument, `name` is valid Java identifier name
and `type` is optional type of generated parameter.

You can use simplified forms like a `{index}` or `{name}` for other bundles, or
in the reference one to reuse defined argument.

### Property Arguments

Property arguments are similar to raw, but have validation support.
Actual formats:
- `#{property.key}` for properties without arguments, but `#{property.key()}` also acceptable.
- `#{property.key(arg1, arg2)}` for properties with arguments. Arguments must match parameters of specified property
  and must be present in the _declaring_ property.
- `#{property.key[amount]}` for plural properties. The `amount` argument may have different name,
  but must be declared with Java `long` type in parameters

### Raw Arguments

Raw arguments are defined in format `${code}`. They can be used for raw strings or code.
