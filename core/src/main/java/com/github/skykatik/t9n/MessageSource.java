package com.github.skykatik.t9n;

public abstract class MessageSource {

    public abstract LocaleTag localeTag();

    public abstract int pluralForm(long value);
}
