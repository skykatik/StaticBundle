package com.github.skykatik.t9n;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static final String indent = " ".repeat(4);
    static final String className = "CustomMessageSource";
    static final String packageName = "com.github.skykatik.t9n";
    static final String pluralKeyFormat = "{base}[{form}]";

    static final Pattern PLURAL_KEY = Pattern.compile(
            "^" +
                    pluralKeyFormat
                            .replace("[", "\\[")
                            .replace("]", "\\]")
                            .replace("{base}", "([a-z.0-9]+)")
                            .replace("{form}", "(\\d+)")
                    + "$",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws IOException {

        var locales = List.of(
                new LocaleSettings(Locale.ROOT, 0, 4, "value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2"),
                new LocaleSettings(new Locale("en"), 1, 2, "value == 1 ? 0 : 1")
        );
        var processingResources = new ProcessingResources(locales);

        Path sourceFile = Path.of("core/src/main/java/com/github/skykatik/t9n/", className + ".java");
        Files.deleteIfExists(sourceFile);

        try (var writer = Files.newBufferedWriter(sourceFile)) {
            writer.append("package ");
            writer.append(packageName);
            writer.append(';');

            writer.newLine();
            writer.newLine();

            writer.append("public final class ");
            writer.append(className);
            writer.append(" extends MessageSource {");
            writer.newLine();

            generateLocaleTagConstants(locales, writer);

            writer.newLine();
            writer.append(indent).append("public ").append(className).append("(int localeTag) {");
            writer.newLine();
            writer.append(indent.repeat(2)).append("super(localeTag);");
            writer.newLine();
            writer.append(indent).append('}');
            writer.newLine();

            generateWithLocaleTagMethod(locales, writer);
            generatePluralFormMethod(locales, writer);

            var properties = new TreeMap<String, Property>();

            var referenceSettings = locales.get(0);
            var referenceBundle = loadProperties("messages", referenceSettings.locale);
            for (var e : referenceBundle.entrySet()) {
                String key = e.getKey();
                String text = e.getValue();

                var p = Property.parse(processingResources, referenceSettings, key, text);
                properties.compute(p.key(), (k, v) -> {
                    if (v == null) {
                        return p;
                    }
                    v.merge(referenceSettings, key, text);
                    return v;
                });
            }

            for (int i = 1; i < locales.size(); i++) {
                var settings = locales.get(i);
                var bundle = loadProperties("messages", settings.locale);
                for (var e : bundle.entrySet()) {
                    String k = e.getKey();
                    String v = e.getValue();

                    Matcher m;
                    String baseKey = (m = PLURAL_KEY.matcher(k)).matches() ? m.group(1) : k;

                    var property = properties.get(baseKey);
                    if (property == null) {
                        throw new IllegalStateException(baseKey);
                    }

                    property.merge(settings, k, v);
                }
            }

            for (var msg : properties.values()) {
                writer.newLine();
                writer.append(indent).append("public String ").append(msg.methodName());
                writer.append('(');

                if (msg instanceof OrdinalProperty p) {
                    generateOrdinalPropertyMethod(locales, writer, p);
                } else if (msg instanceof PluralProperty p) {
                    generatePluralPropertyMethod(locales, writer, p);
                } else {
                    throw new IllegalStateException();
                }
            }

            writer.append('}');
            writer.newLine();
        }
    }

    private static void generatePluralPropertyMethod(List<LocaleSettings> locales, BufferedWriter writer, PluralProperty p) throws IOException {
        writer.append("long amount");
        writer.append(") {");
        writer.newLine();

        writer.append(indent.repeat(2)).append("int index = pluralForm(amount);");
        writer.newLine();

        writer.append(indent.repeat(2)).append("return switch (localeTag) {");
        writer.newLine();

        for (int localeTag = 0; localeTag < locales.size(); localeTag++) {
            var localeSettings = locales.get(localeTag);

            writer.append(indent.repeat(3)).append("case ");
            writer.append(localeSettings.localeTag);
            writer.append(" -> switch (index) {");
            writer.newLine();

            var pluralForms = p.messages[localeTag];

            for (int pluralForm = 0; pluralForm < pluralForms.length; pluralForm++) {
                var message = pluralForms[pluralForm];

                writer.append(indent.repeat(4)).append("case ").append(Integer.toString(pluralForm)).append(" -> ");
                for (int i = 0, k = 0; i < message.tokens.length; i++) {
                    writer.append(message.tokens[i]);

                    if (k < message.args.length) {
                        Arg arg = message.args[k++];

                        writer.append(" + ");
                        writer.append(arg.name);
                    }

                    if (i != message.tokens.length - 1) {
                        writer.append(" + ");
                    }
                }
                writer.append(';');
                writer.newLine();
            }

            writer.append(indent.repeat(4)).append("default -> throw new IllegalStateException();");;
            writer.newLine();

            writer.append(indent.repeat(3)).append("};");
            writer.newLine();
        }

        writer.append(indent.repeat(3)).append("default -> throw new IllegalStateException();");
        writer.newLine();

        writer.append(indent.repeat(2)).append("};");
        writer.newLine();

        writer.append(indent).append('}');
        writer.newLine();
    }

    private static void generateOrdinalPropertyMethod(List<LocaleSettings> locales, BufferedWriter writer, OrdinalProperty p) throws IOException {

        var referenceMessage = p.messages[0];
        for (int i = 0; i < referenceMessage.args.length; i++) {
            Arg arg = referenceMessage.args[i];
            writer.append(arg.type).append(" ").append(arg.name);

            if (i != referenceMessage.args.length - 1) {
                writer.append(", ");
            }
        }

        writer.append(") {");
        writer.newLine();
        writer.append(indent.repeat(2)).append("return switch (localeTag) {");
        writer.newLine();

        for (int localeTag = 0; localeTag < locales.size(); localeTag++) {
            var localeSettings = locales.get(localeTag);

            writer.append(indent.repeat(3)).append("case ");
            writer.append(localeSettings.localeTag);
            writer.append(" -> ");

            var message = p.messages[localeTag];
            for (int i = 0, k = 0; i < message.tokens.length; i++) {
                writer.append(message.tokens[i]);

                if (k < message.args.length) {
                    Arg arg = message.args[k++];

                    writer.append(" + ");
                    writer.append(arg.name);
                }

                if (i != message.tokens.length - 1) {
                    writer.append(" + ");
                }
            }
            writer.append(';');
            writer.newLine();
        }

        writer.append(indent.repeat(3)).append("default -> throw new IllegalStateException();");
        writer.newLine();

        writer.append(indent.repeat(2)).append("};");
        writer.newLine();

        writer.append(indent).append('}');
        writer.newLine();
    }

    private static void generateLocaleTagConstants(List<LocaleSettings> locales, BufferedWriter writer) throws IOException {
        for (int i = 0; i < locales.size(); i++) {
            var settings = locales.get(i);
            writer.append(indent).append("public static final int ");
            writer.append(settings.localeTag);
            writer.append(" = ");
            writer.append(Integer.toString(i));
            writer.append(';');
            writer.newLine();
        }
    }

    private static void generatePluralFormMethod(List<LocaleSettings> locales, BufferedWriter writer) throws IOException {
        writer.newLine();
        writer.append(indent).append("public int pluralForm(long value) {");
        writer.newLine();
        writer.append(indent.repeat(2)).append("return switch (localeTag) {");
        writer.newLine();
        for (var settings : locales) {
            writer.append(indent.repeat(3)).append("case ").append(settings.localeTag);
            writer.append(" -> ").append(settings.pluralFormFunction).append(';');
            writer.newLine();
        }
        writer.append(indent.repeat(3)).append("default -> throw new IllegalStateException();");
        writer.newLine();
        writer.append(indent.repeat(2)).append("};");
        writer.newLine();
        writer.append(indent).append('}');
        writer.newLine();
    }

    private static void generateWithLocaleTagMethod(List<LocaleSettings> locales, BufferedWriter writer) throws IOException {
        writer.newLine();
        writer.append(indent).append("public ").append(className).append(" withLocaleTag(int localeTag) {");
        writer.newLine();
        writer.append(indent.repeat(2)).append("if (localeTag < 0 || localeTag > ");
        writer.append(Integer.toString(locales.size())).append(") throw new IllegalArgumentException();");
        writer.newLine();
        writer.append(indent.repeat(2)).append("if (this.localeTag == localeTag) return this;");
        writer.newLine();
        writer.append(indent.repeat(2)).append("return new ").append(className).append("(localeTag);");
        writer.newLine();
        writer.append(indent).append('}');
        writer.newLine();
    }

    private static String translateKeyToMethodName(String key) {
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

    static final Pattern ARGS = Pattern.compile("\\{([a-z0-9:{}\\[\\]]+)}", Pattern.CASE_INSENSITIVE);

    record Message(Arg[] args, String[] tokens) {

        static Message parse(String text) {
            var tokens = new ArrayList<String>();
            var args = new ArrayList<Arg>();
            var matcher = ARGS.matcher(text);
            int prev = 0;
            while (matcher.find()) {
                tokens.add(quote(text.substring(prev, matcher.start())));
                String[] nameAndType = matcher.group(1).split(":");
                String name = nameAndType[0];
                String type = "String";
                if (nameAndType.length == 2) {
                    type = typeOf(nameAndType[1]);
                }

                String transformation = null;

                args.add(new Arg(type, name, transformation));

                prev = matcher.end();
            }
            if (prev != text.length()) {
                tokens.add(quote(text.substring(prev)));
            }

            return new Message(args.toArray(EMPTY_ARG_ARRAY), tokens.toArray(EMPTY_STRING_ARRAY));
        }

        static final Arg[] EMPTY_ARG_ARRAY = new Arg[0];
        static final String[] EMPTY_STRING_ARRAY = new String[0];

        static String typeOf(String group) {
            return switch (group.toLowerCase(Locale.ROOT)) {
                case "string" -> "String";
                case "int" -> "int";
                default -> throw new IllegalStateException(group);
            };
        }

        @Override
        public String toString() {
            return "Message{" +
                    "args=" + Arrays.toString(args) +
                    ", messages=" + Arrays.toString(tokens) +
                    '}';
        }
    }

    record Arg(String type, String name, String transformation) {
    }

    record OrdinalProperty(String key, String methodName, Message[/*localeTag*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, String key, String text) {
            messages[settings.localeTagValue] = Message.parse(text);
        }

        @Override
        public String toString() {
            return "OrdinalProperty{" +
                    "key='" + key + '\'' +
                    ", methodName='" + methodName + '\'' +
                    ", messages=" + Arrays.toString(messages) +
                    '}';
        }
    }

    record PluralProperty(String key, String methodName, Message[/*localeTag*/][/*pluralForm*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, String key, String text) {
            var matcher = PLURAL_KEY.matcher(key);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Ordinal property with plural: '" +
                        key + "' in locale: " + settings.locale);
            }

            int pluralForm = Integer.parseInt(matcher.group(2));

            // if (pluralForm < 0 || pluralForm >= messages[settings.localeTagValue].length) {
            //     throw new IllegalArgumentException("Incorrect plural form for key '" + key +
            //             "': " + pluralForm + " ('" + text + "'), plural forms count: " + settings.pluralFormsCount +
            //             " in locale " + settings.locale);
            // }

            var msg = Message.parse(text);
            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }

            locale[pluralForm] = msg;
        }
    }

    sealed interface Property {

        static Property parse(ProcessingResources processingResources,
                              LocaleSettings settings, String key, String text) {
            var matcher = PLURAL_KEY.matcher(key);
            if (matcher.matches()) {
                int pluralForm = Integer.parseInt(matcher.group(2));

                if (pluralForm < 0 || pluralForm >= settings.pluralFormsCount) {
                    throw new IllegalArgumentException("Incorrect plural form for key '" + key +
                            "': " + pluralForm + ", plural forms count: " + settings.pluralFormsCount +
                            " in locale " + settings.locale);
                }

                key = matcher.group(1);

                var msg = Message.parse(text);
                var messages = new Message[processingResources.locales.size()][];

                var locale = messages[settings.localeTagValue];
                if (locale == null) {
                    messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
                }
                locale[pluralForm] = msg;

                return new PluralProperty(key, translateKeyToMethodName(key), messages);
            }

            var msg = Message.parse(text);
            var tokens = new Message[processingResources.locales.size()];
            tokens[settings.localeTagValue] = msg;

            return new OrdinalProperty(key, translateKeyToMethodName(key), tokens);
        }

        String key();

        String methodName();

        void merge(LocaleSettings settings, String key, String text);
    }

    static String quote(String text) {
        return "\"" + text + "\"";
    }

    static String translateLocaleToLocaleTag(Locale locale) {
        final String postfix = "_LOCALE_TAG";
        if (locale.equals(Locale.ROOT)) {
            return "ROOT" + postfix;
        }
        return locale.toString().toUpperCase(Locale.ROOT) + postfix;
    }

    record LocaleSettings(Locale locale, String localeTag, int localeTagValue,
                          int pluralFormsCount, String pluralFormFunction) {

        LocaleSettings(Locale locale, int localeTagValue, int pluralFormsCount, String pluralFormFunction) {
            this(locale, translateLocaleToLocaleTag(locale), localeTagValue, pluralFormsCount, pluralFormFunction);
        }
    }

    record ProcessingResources(List<LocaleSettings> locales) {

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, String> loadProperties(String baseName, Locale locale) throws IOException {
        Path p = Path.of("core/src/main/resources", toBundleName(baseName, locale) + ".properties");
        try (var reader = Files.newBufferedReader(p)) {
            var props = new Properties();
            props.load(reader);
            var map = new HashMap(props);
            return (Map<String, String>) map;
        }
    }

    static String toBundleName(String baseName, Locale locale) {
        if (locale == Locale.ROOT) {
            return baseName;
        }

        String language = locale.getLanguage();
        String script = locale.getScript();
        String country = locale.getCountry();
        String variant = locale.getVariant();

        if (language == "" && country == "" && variant == "") {
            return baseName;
        }

        StringBuilder sb = new StringBuilder(baseName);
        sb.append('_');
        if (script != "") {
            if (variant != "") {
                sb.append(language).append('_').append(script).append('_').append(country).append('_').append(variant);
            } else if (country != "") {
                sb.append(language).append('_').append(script).append('_').append(country);
            } else {
                sb.append(language).append('_').append(script);
            }
        } else {
            if (variant != "") {
                sb.append(language).append('_').append(country).append('_').append(variant);
            } else if (country != "") {
                sb.append(language).append('_').append(country);
            } else {
                sb.append(language);
            }
        }
        return sb.toString();

    }
}
