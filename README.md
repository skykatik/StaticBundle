## Static Bundle

A tiny processor for genering Java classes from specfiied `.properties` files.
The resulting classes contain accessor methods which return bundle string depending on locale.

In `messages.properties` content should be:

```properties
bundle.key = translation for 'bundle.key'
```

And then generated class will be similar to this:
```java
public CustomMessageSource extends MessageSource {
    ...

    public String bundleKey() {
        return switch (localeTag) {
            case ROOT -> "translation for 'bundle.key'"
        };
    }
}
```

Yeah, generated bundle class doesn't simular to `ResourceBundle` or `Properties`, and doesn't uses any `HashMap` for properties
