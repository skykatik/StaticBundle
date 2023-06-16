package com.github.skykatik.t9n.gen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface PropertyKeyNaming {

    static PropertyKeyNaming instance() {
        class DefaultImpl implements PropertyKeyNaming {
            static final DefaultImpl INSTANCE = new DefaultImpl();

            static final Pattern PATTERN = Pattern.compile("^([\\w._\\-]+)(\\[(\\d)])?$");

            @Override
            public String format(String baseKey, int pluralForm) {
                return baseKey + "[" + pluralForm + "]";
            }

            @Override
            public Parts parse(String key) {
                Matcher m = PATTERN.matcher(key);
                if (m.matches()) {
                    String baseKey = m.group(1);
                    String pluralForm = m.group(3);
                    int pluralFormInt = pluralForm != null ? Integer.parseInt(pluralForm) : -1;
                    return new Parts(baseKey, pluralFormInt);
                }
                throw new IllegalArgumentException("Invalid property key: '" + key + "'");
            }
        }

        return DefaultImpl.INSTANCE;
    }

    String format(String baseKey, int pluralForm);

    Parts parse(String key);

    record Parts(String baseKey, int pluralForm) { }
}
