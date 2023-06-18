## Static Bundle

A tiny processor for generating Java classes from specified `.properties` files.
The resulting classes contain accessor methods which return bundle string depending on locale.

In `messages.properties` content should be:

```properties
bundle.key = translation for 'bundle.key'
```

And then generated class will be similar to this:
```java
public class CustomMessageSource extends MessageSource {
    ...

    public String bundleKey() {
        return switch (localeTag) {
            case ROOT -> "translation for 'bundle.key'"
        };
    }
}
```

Yeah, generated bundle class doesn't similar to `ResourceBundle` or `Properties`, and doesn't use any `HashMap` for properties
