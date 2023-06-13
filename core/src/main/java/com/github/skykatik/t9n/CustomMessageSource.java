package com.github.skykatik.t9n;

import java.util.Locale;
import java.util.Objects;

public final class CustomMessageSource extends MessageSource {
    private final LocaleTag localeTag;

    public CustomMessageSource(LocaleTag localeTag) {
        this.localeTag = Objects.requireNonNull(localeTag);
    }

    public LocaleTag localeTag() {
        return localeTag;
    }

    public CustomMessageSource withLocaleTag(LocaleTag localeTag) {
        if (this.localeTag == localeTag) return this;
        return new CustomMessageSource(localeTag);
    }

    public int pluralForm(long value) {
        return switch (localeTag) {
            case ROOT -> value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2;
            case EN -> value == 1 ? 0 : 1;
        };
    }

    public String commandsAnotherMessage(int time) {
        return switch (localeTag) {
            case ROOT -> "Время " + time + " и 'столько' минут {plural.minutes[time]}";
            case EN -> "Time: " + time + ", plural minutes: {plural.minutes[time]}";
        };
    }

    public String commandsTestMessage(String playerName, String reason) {
        return switch (localeTag) {
            case ROOT -> "Игрок " + playerName + " и причина " + reason;
            case EN -> "Reason: " + reason + ", Player name: " + playerName;
        };
    }

    public String pluralMinutes(long amount) {
        int index = pluralForm(amount);
        return switch (localeTag) {
            case ROOT -> switch (index) {
                case 0 -> "предупреждение";
                case 1 -> "предупреждения";
                case 2 -> "предупреждений";
                case 3 -> "предупреждение";
                default -> throw new IllegalStateException();
            };
            case EN -> switch (index) {
                case 0 -> "min";
                case 1 -> "mins";
                default -> throw new IllegalStateException();
            };
        };
    }

    public String simple() {
        return switch (localeTag) {
            case ROOT -> "простой";
            case EN -> "simple";
        };
    }

    public enum LocaleTag {
        ROOT(Locale.ROOT),
        EN(new Locale("en"));

        private final Locale locale;

        LocaleTag(Locale locale) {
            this.locale = locale;
        }
        public Locale locale() {
            return locale;
        }
    }
}
