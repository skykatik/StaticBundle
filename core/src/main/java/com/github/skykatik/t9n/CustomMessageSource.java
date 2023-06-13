package com.github.skykatik.t9n;

public final class CustomMessageSource extends MessageSource {
    public static final int ROOT_LOCALE_TAG = 0;
    public static final int EN_LOCALE_TAG = 1;

    public CustomMessageSource(int localeTag) {
        super(localeTag);
    }

    public CustomMessageSource withLocaleTag(int localeTag) {
        if (localeTag < 0 || localeTag > 2) throw new IllegalArgumentException();
        if (this.localeTag == localeTag) return this;
        return new CustomMessageSource(localeTag);
    }

    public int pluralForm(long value) {
        return switch (localeTag) {
            case ROOT_LOCALE_TAG -> value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2;
            case EN_LOCALE_TAG -> value == 1 ? 0 : 1;
            default -> throw new IllegalStateException();
        };
    }

    public String commandsAnotherMessage(int time) {
        return switch (localeTag) {
            case ROOT_LOCALE_TAG -> "Время " + time + " и 'столько' минут {plural.minutes[time]}";
            case EN_LOCALE_TAG -> "Time: " + time + ", plural minutes: {plural.minutes[time]}";
            default -> throw new IllegalStateException();
        };
    }

    public String commandsTestMessage(String playerName, String reason) {
        return switch (localeTag) {
            case ROOT_LOCALE_TAG -> "Игрок " + playerName + " и причина " + reason;
            case EN_LOCALE_TAG -> "Reason: " + reason + ", Player name: " + playerName;
            default -> throw new IllegalStateException();
        };
    }

    public String pluralMinutes(long amount) {
        int index = pluralForm(amount);
        return switch (localeTag) {
            case ROOT_LOCALE_TAG -> switch (index) {
                case 0 -> "предупреждение";
                case 1 -> "предупреждения";
                case 2 -> "предупреждений";
                case 3 -> "предупреждение";
                default -> throw new IllegalStateException();
            };
            case EN_LOCALE_TAG -> switch (index) {
                case 0 -> "min";
                case 1 -> "mins";
                default -> throw new IllegalStateException();
            };
            default -> throw new IllegalStateException();
        };
    }

    public String simple() {
        return switch (localeTag) {
            case ROOT_LOCALE_TAG -> "простой";
            case EN_LOCALE_TAG -> "simple";
            default -> throw new IllegalStateException();
        };
    }
}
