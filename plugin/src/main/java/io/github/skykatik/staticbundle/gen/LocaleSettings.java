package io.github.skykatik.staticbundle.gen;

import java.util.Locale;

final class LocaleSettings {
    final Locale locale;
    final String localeTag;
    final int localeTagValue;
    final int pluralFormsCount;
    final String pluralFormFunction;

    String relativeResourcePath;

    LocaleSettings(Locale locale, String localeTag, int localeTagValue,
                   int pluralFormsCount, String pluralFormFunction) {
        this.locale = locale;
        this.localeTag = localeTag;
        this.localeTagValue = localeTagValue;
        this.pluralFormsCount = pluralFormsCount;
        this.pluralFormFunction = pluralFormFunction;
    }

    LocaleSettings(io.github.skykatik.staticbundle.plugin.LocaleSettings internal, int localeTagValue) {
        this(internal.getLocale().get(), localeTagValue, internal.getPluralForms().get(),
                internal.getPluralFunction().get());
    }

    LocaleSettings(Locale locale, int localeTagValue, int pluralFormsCount, String pluralFormFunction) {
        this(locale, translateLocaleToTag(locale), localeTagValue, pluralFormsCount, pluralFormFunction);
    }

    IllegalStateException problem(String key, String text) {
        return new IllegalStateException("[Bundle: '" + relativeResourcePath + "', property: '" + key + "'] " + text);
    }

    static String translateLocaleToTag(Locale locale) {
        if (locale.equals(Locale.ROOT)) {
            return "ROOT";
        }
        return locale.toString().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LocaleSettings{" +
                "locale=" + locale +
                ", localeTag='" + localeTag + '\'' +
                ", localeTagValue=" + localeTagValue +
                ", pluralFormsCount=" + pluralFormsCount +
                ", pluralFormFunction='" + pluralFormFunction + '\'' +
                ", relativeResourcePath='" + relativeResourcePath + '\'' +
                '}';
    }
}
