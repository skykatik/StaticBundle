package io.github.skykatik.staticbundle;

public abstract class MessageSource {

    public abstract LocaleTag localeTag();

    public abstract int pluralForm(long value);
}
