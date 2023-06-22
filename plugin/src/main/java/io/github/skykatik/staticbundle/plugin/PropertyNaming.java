package io.github.skykatik.staticbundle.plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface PropertyNaming {

    static PropertyNaming instance() {
        class DefaultImpl implements PropertyNaming {
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

            @Override
            public String toMethodName(String key) {
                char[] result = new char[key.length()];
                int d = 0;
                boolean prevIsDot = false;
                for (int i = 0; i < key.length(); i++) {
                    char c = key.charAt(i);

                    if (c == '.') {
                        prevIsDot = true;
                    } else if (prevIsDot) {
                        result[d++] = Character.toUpperCase(c);
                        prevIsDot = false;
                    } else {
                        result[d++] = c;
                    }
                }

                return new String(result, 0, d);
            }

            @Override
            public String toString() {
                return "DefaultPropertyKeyNaming.instance()";
            }
        }

        return DefaultImpl.INSTANCE;
    }

    String format(String baseKey, int pluralForm);

    Parts parse(String key);

    String toMethodName(String key);

    record Parts(String baseKey, int pluralForm) { }
}
