package com.github.skykatik.t9n.gen;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Pattern;

@SupportedAnnotationTypes("com.github.skykatik.t9n.gen.MessageSource")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        var sources = roundEnv.getElementsAnnotatedWith(MessageSource.class);
        for (Element source : sources) {
            if (source instanceof PackageElement p &&
                    p.getEnclosingElement() instanceof ModuleElement m) {

                var qualifiedName = p.getQualifiedName();
                var ann = p.getAnnotation(MessageSource.class);

                try {
                    generate(source, m.getQualifiedName(), qualifiedName, ann);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to generate bundle class", e);
                }
            }
        }

        return true;
    }

    static final String indent = " ".repeat(4);
    static final int lineWrap = 120;

    String className;
    String packageName;
    String moduleName;
    String moduleAndPackageNames;

    void generate(Element source, Name moduleName, Name packageName, MessageSource annotation) throws IOException {
        className = annotation.className();
        this.packageName = packageName.toString();
        this.moduleName = moduleName.toString();

        if (!this.moduleName.isEmpty()) {
            moduleAndPackageNames = this.moduleName + "/" + this.packageName;
        } else {
            moduleAndPackageNames = this.packageName;
        }

        String name = moduleAndPackageNames + "." + className;
        var sourceFile = processingEnv.getFiler().createSourceFile(name, source);
        try (CharSink sink = new CharSink(sourceFile.openWriter(), indent, lineWrap)) {

            var locales = List.of(
                    new LocaleSettings(Locale.ROOT, 0, 4, "value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2"),
                    new LocaleSettings(new Locale("en"), 1, 2, "value == 1 ? 0 : 1")
            );
            var processingResources = new ProcessingResources(locales);

            sink.append("package ").append(this.packageName).append(';');
            sink.ln(2);

            sink.append("import java.util.Locale;").ln();
            sink.append("import java.util.Objects;").ln();
            sink.append("import com.github.skykatik.t9n.MessageSource;").ln();
            sink.append("import com.github.skykatik.t9n.LocaleTag;").ln();
            // TODO library imports
            sink.ln();

            sink.append("public final class ").append(className).append(" extends MessageSource");
            sink.begin();

            sink.append("final LocaleTag localeTag;");
            sink.ln();

            sink.ln();
            sink.append("public ").append(className).append("(LocaleTag localeTag)");
            sink.begin();
            sink.append("this.localeTag = Objects.requireNonNull(localeTag);");
            sink.end();

            sink.ln();
            sink.append("public LocaleTag localeTag()");
            sink.begin();
            sink.append("return localeTag;");
            sink.end();

            generateWithLocaleTagMethod(sink);
            generatePluralFormMethod(locales, sink);

            var properties = new TreeMap<String, Property>();

            var referenceSettings = locales.get(0);
            var referenceBundle = loadProperties(annotation.baseName(), referenceSettings.locale);
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
                var bundle = loadProperties(annotation.baseName(), settings.locale);
                for (var e : bundle.entrySet()) {
                    String k = e.getKey();
                    String v = e.getValue();

                    var parts = PropertyKeyNaming.instance().parse(k);

                    var property = properties.get(parts.baseKey());
                    if (property == null) {
                        throw new IllegalStateException(parts.baseKey());
                    }

                    property.merge(settings, k, v);
                }
            }

            for (var msg : properties.values()) {
                sink.ln();
                sink.append("public String ").append(msg.methodName()).append('(');

                if (msg instanceof OrdinalProperty p) {
                    generateOrdinalPropertyMethod(locales, sink, p);
                } else if (msg instanceof PluralProperty p) {
                    generatePluralPropertyMethod(locales, sink, p);
                } else {
                    throw new IllegalStateException();
                }
            }

            generateLocaleTagConstants(locales, sink);

            sink.end();
        }
    }

    static void generatePluralPropertyMethod(List<LocaleSettings> locales, CharSink sink, PluralProperty p) throws IOException {
        sink.append("long amount").append(')');
        sink.begin();

        sink.append("int index = pluralForm(amount);");
        sink.ln();

        sink.append("return switch (localeTag)");
        sink.begin();

        for (int localeTag = 0; localeTag < locales.size(); localeTag++) {
            var localeSettings = locales.get(localeTag);

            sink.append("case ").append(localeSettings.localeTag).append(" -> switch (index)");
            sink.begin();

            var pluralForms = p.messages[localeTag];

            for (int pluralForm = 0; pluralForm < pluralForms.length; pluralForm++) {
                var message = pluralForms[pluralForm];

                sink.append("case ").append(Integer.toString(pluralForm)).append(" -> ");
                for (int i = 0, k = 0; i < message.tokens.length; i++) {
                    sink.append(message.tokens[i]);

                    if (k < message.args.length) {
                        Arg arg = message.args[k++];

                        sink.append(" + ");
                        sink.append(arg.name);
                    }

                    if (i != message.tokens.length - 1) {
                        sink.append(" + ");
                    }
                }
                sink.append(';');
                sink.ln();
            }

            sink.append("default -> throw new IllegalStateException();");
            sink.endsc();
        }

        sink.endsc();

        sink.end();
    }

    static void generateOrdinalPropertyMethod(List<LocaleSettings> locales, CharSink sink, OrdinalProperty p) throws IOException {

        var referenceMessage = p.messages[0];
        for (int i = 0; i < referenceMessage.args.length; i++) {
            Arg arg = referenceMessage.args[i];
            sink.append(arg.type).append(" ").append(arg.name);

            if (i != referenceMessage.args.length - 1) {
                sink.append(", ");
            }
        }

        sink.append(')');
        sink.begin();

        sink.append("return switch (localeTag)");
        sink.begin();
        for (int localeTag = 0; localeTag < locales.size(); localeTag++) {
            var localeSettings = locales.get(localeTag);

            sink.append("case ").append(localeSettings.localeTag).append(" -> ");

            var message = p.messages[localeTag];
            for (int i = 0, k = 0; i < message.tokens.length; i++) {
                sink.append(message.tokens[i]);

                if (k < message.args.length) {
                    Arg arg = message.args[k++];

                    sink.append(" + ");
                    sink.append(arg.name);
                }

                if (i != message.tokens.length - 1) {
                    sink.append(" + ");
                }
            }

            sink.append(';');
            sink.ln();
        }


        sink.endsc();

        sink.end();
    }

    static void generateLocaleTagConstants(List<LocaleSettings> locales, CharSink sink) throws IOException {
        sink.ln();
        sink.append("public enum LocaleTag implements com.github.skykatik.t9n.LocaleTag");
        sink.begin();

        for (int i = 0; i < locales.size(); i++) {
            var settings = locales.get(i);

            sink.append(settings.localeTag);
            sink.append('(');
            sink.append(localeToString(settings.locale));
            sink.append(')');

            if (i != locales.size() - 1) {
                sink.append(',');
                sink.ln();
            }
        }

        sink.append(';');
        sink.ln(2);

        sink.append("final Locale locale;");
        sink.ln(2);

        sink.append("LocaleTag(Locale locale)");
        sink.begin();
        sink.append("this.locale = locale;");
        sink.end();

        sink.ln();
        sink.append("public Locale locale()");
        sink.begin();
        sink.append("return locale;");
        sink.end();

        sink.end();
    }

    static String localeToString(Locale locale) {
        if (locale.equals(Locale.ROOT)) {
            return "Locale.ROOT";
        } else if (locale.equals(Locale.ENGLISH)) {
            return "Locale.ENGLISH";
        } else if (locale.equals(Locale.FRENCH)) {
            return "Locale.FRENCH";
        } else if (locale.equals(Locale.GERMAN)) {
            return "Locale.GERMAN";
        } else if (locale.equals(Locale.ITALIAN)) {
            return "Locale.ITALIAN";
        } else if (locale.equals(Locale.JAPANESE)) {
            return "Locale.JAPANESE";
        } else if (locale.equals(Locale.KOREAN)) {
            return "Locale.KOREAN";
        } else if (locale.equals(Locale.CHINESE)) {
            return "Locale.CHINESE";
        } else if (locale.equals(Locale.SIMPLIFIED_CHINESE)) {
            return "Locale.SIMPLIFIED_CHINESE";
        } else if (locale.equals(Locale.TRADITIONAL_CHINESE)) {
            return "Locale.TRADITIONAL_CHINESE";
        } else if (locale.equals(Locale.FRANCE)) {
            return "Locale.FRANCE";
        } else if (locale.equals(Locale.GERMANY)) {
            return "Locale.GERMANY";
        } else if (locale.equals(Locale.ITALY)) {
            return "Locale.ITALY";
        } else if (locale.equals(Locale.JAPAN)) {
            return "Locale.JAPAN";
        } else if (locale.equals(Locale.KOREA)) {
            return "Locale.KOREA";
        } else if (locale.equals(Locale.UK)) {
            return "Locale.UK";
        } else if (locale.equals(Locale.US)) {
            return "Locale.US";
        } else if (locale.equals(Locale.CANADA)) {
            return "Locale.CANADA";
        } else if (locale.equals(Locale.CANADA_FRENCH)) {
            return "Locale.CANADA_FRENCH";
        }

        String s = String.join(", ", makeLiteral(locale.getLanguage()),
                makeLiteral(locale.getCountry()), makeLiteral(locale.getVariant()));
        return "new Locale(" + s + ')';
    }

    static void generatePluralFormMethod(List<LocaleSettings> locales, CharSink sink) throws IOException {
        sink.ln();

        sink.append("public int pluralForm(long value)");
        sink.begin();
        sink.append("return switch (localeTag)");
        sink.begin();
        for (var settings : locales) {
            sink.append("case ").append(settings.localeTag).append(" -> ").append(settings.pluralFormFunction).append(';');
            sink.ln();
        }

        sink.endsc();
        sink.end();
    }

    void generateWithLocaleTagMethod(CharSink sink) throws IOException {
        sink.ln();

        sink.append("public ").append(className).append(" withLocaleTag(LocaleTag localeTag)");
        sink.begin();
        sink.append("if (this.localeTag == localeTag) return this;");
        sink.ln();
        sink.append("return new ").append(className).append("(localeTag);");
        sink.end();
    }

    static String translateKeyToMethodName(String key) {
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
                tokens.add(makeLiteral(text.substring(prev, matcher.start())));
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
                tokens.add(makeLiteral(text.substring(prev)));
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

    record PluralProperty(String key, String methodName,
                          Message[/*localeTag*/][/*pluralForm*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, String key, String text) {
            var parts = PropertyKeyNaming.instance().parse(key);
            if (parts.pluralForm() == -1) {
                throw new IllegalArgumentException("Ordinal property with plural: '" +
                        key + "' in locale: " + settings.locale);
            }

            var msg = Message.parse(text);
            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }

            locale[parts.pluralForm()] = msg;
        }
    }

    sealed interface Property {

        static Property parse(ProcessingResources processingResources,
                              LocaleSettings settings, String key, String text) {


            var parts = PropertyKeyNaming.instance().parse(key);
            if (parts.pluralForm() != -1) {

                if (parts.pluralForm() < 0 || parts.pluralForm() >= settings.pluralFormsCount) {
                    throw new IllegalArgumentException("Incorrect plural form for key '" + key +
                            "': " + parts.pluralForm() + ", plural forms count: " + settings.pluralFormsCount +
                            " in locale " + settings.locale);
                }

                var msg = Message.parse(text);
                var messages = new Message[processingResources.locales.size()][];

                var locale = messages[settings.localeTagValue];
                if (locale == null) {
                    messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
                }
                locale[parts.pluralForm()] = msg;

                String baseKey = parts.baseKey();
                return new PluralProperty(baseKey, translateKeyToMethodName(baseKey), messages);
            }

            var msg = Message.parse(text);
            var tokens = new Message[processingResources.locales.size()];
            tokens[settings.localeTagValue] = msg;

            return new OrdinalProperty(parts.baseKey(), translateKeyToMethodName(parts.baseKey()), tokens);
        }

        String key();

        String methodName();

        void merge(LocaleSettings settings, String key, String text);
    }

    static String escape(char c) {
        return switch (c) {
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\'' -> "\\'";
            case '\"' -> "\\\"";
            case '\\' -> "\\\\";
            default -> String.valueOf(c);
        };
    }

    static String makeLiteral(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                out.append(c);
            } else {
                out.append(escape(c));
            }
        }
        out.append('"');
        return out.toString();
    }

    static String translateLocaleToLocaleTag(Locale locale) {
        if (locale.equals(Locale.ROOT)) {
            return "ROOT";
        }
        return locale.toString().toUpperCase(Locale.ROOT);
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
    Map<String, String> loadProperties(String baseName, Locale locale) throws IOException {
        int endOfPackage = baseName.lastIndexOf('.');
        String resourcePackage = endOfPackage != -1 ? '.' + baseName.substring(0, endOfPackage) : "";
        String resourceName = endOfPackage != -1 ? baseName.substring(endOfPackage + 1) : baseName;

        String bundleName = toBundleName(resourceName, locale) + ".properties";

        var resource = processingEnv.getFiler().getResource(StandardLocation.SOURCE_PATH, packageName + resourcePackage, bundleName);
        if (resource == null) {
            throw new FileNotFoundException(baseName);
        }

        try (var reader = resource.openReader(false)) {
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
