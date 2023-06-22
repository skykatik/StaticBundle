package io.github.skykatik.t9n.gen;

import io.github.skykatik.staticbundle.plugin.DefaultSourceSetSettings;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class StaticBundleProcessor {

    static final int REFERENCE_LOCALE_TAG = 0;
    static final Pattern ARGS = Pattern.compile("\\{(\\d+:)?([a-z0-9:{}\\[\\]]+)}", Pattern.CASE_INSENSITIVE);
    static final String indent = " ".repeat(4);
    static final int lineWrap = 120;

    final Project project;
    final Path resultPath;
    final String packageName;
    final String className;
    final String resourceFilenameFormat;
    final FileCollection resources;
    final ProcessingResources procResources;
    final TreeMap<String, Property> properties = new TreeMap<>();

    public StaticBundleProcessor(Project project, Directory codegenDir,
                                 FileCollection resources, DefaultSourceSetSettings sett) {

        this.project = project;
        this.resources = resources;

        resourceFilenameFormat = sett.getResourceFilenameFormat().get();

        String baseName = sett.getMessageSourceClassName().get();
        int lastDot = baseName.lastIndexOf('.');
        packageName = lastDot != -1 ? baseName.substring(0, lastDot) : "";
        className = lastDot != -1 ? baseName.substring(lastDot + 1) : baseName;

        String translated = baseName.replace('.', '/');
        resultPath = codegenDir.file(translated + ".java").getAsFile().toPath();

        var internalSettings = sett.getSettings();
        var locales = new ArrayList<LocaleSettings>(internalSettings.size());
        for (int i = 0; i < internalSettings.size(); i++) {
            var internal = internalSettings.get(i);

            locales.add(new LocaleSettings(internal, i));
        }

        procResources = new ProcessingResources(locales);
    }

    public void validate() throws IOException {

        var referenceSettings = procResources.locales.get(REFERENCE_LOCALE_TAG);
        var referenceBundle = loadBundle(referenceSettings.locale);
        referenceSettings.relativeResourcePath = project.relativePath(referenceBundle.resourcePath);
        for (var e : referenceBundle.properties.entrySet()) {
            String key = e.getKey();
            String text = e.getValue();

            var p = parseProperty(referenceSettings, key, text);
            properties.compute(p.key(), (k, v) -> {
                if (v == null) {
                    return p;
                }
                v.merge(referenceSettings, key, text);
                return v;
            });
        }

        checkForMissingPluralForms(referenceSettings);

        for (int i = 1; i < procResources.locales.size(); i++) {
            var settings = procResources.locales.get(i);
            var bundle = loadBundle(settings.locale);
            settings.relativeResourcePath = project.relativePath(bundle.resourcePath);

            for (var e : bundle.properties.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();

                var parts = PropertyKeyNaming.instance().parse(k);
                var property = properties.get(parts.baseKey());
                if (property == null) {
                    throw settings.problem(k, "Extraneous property");
                }

                property.merge(settings, k, v);
            }

            checkForMissingPluralForms(settings);
        }
    }

    public void generate() throws IOException {
        Files.createDirectories(resultPath.getParent());

        try (CharSink sink = new CharSink(Files.newBufferedWriter(resultPath), indent, lineWrap)) {
            if (!packageName.isEmpty()) {
                sink.append("package ").append(packageName).append(';');
                sink.ln(2);
            }

            sink.append("""
                    import java.util.Locale;
                    import java.util.Objects;
                    import io.github.skykatik.staticbundle.MessageSource;
                    import io.github.skykatik.staticbundle.LocaleTag;
                    
                    """);

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
            generatePluralFormMethod(sink);

            for (var msg : properties.values()) {
                sink.ln();
                sink.append("public String ").append(msg.methodName()).append('(');

                if (msg instanceof OrdinalProperty p) {
                    generateOrdinalPropertyMethod(sink, p);
                } else if (msg instanceof PluralProperty p) {
                    generatePluralPropertyMethod(sink, p);
                } else {
                    throw new IllegalStateException();
                }
            }

            generateLocaleTagConstants(sink);

            sink.end();
        }
    }

    void checkForMissingPluralForms(LocaleSettings settings) {
        for (Property value : properties.values()) {
            if (value instanceof PluralProperty p) {
                var pluralForms = p.messages[settings.localeTagValue];
                for (int n = 0; n < pluralForms.length; n++) {
                    var pluralForm = pluralForms[n];
                    if (pluralForm == null) {
                        String key = PropertyKeyNaming.instance().format(p.key, n);
                        throw settings.problem(key, "Missing plural property");
                    }
                }
            } else if (value instanceof OrdinalProperty p) {
                if (p.messages[settings.localeTagValue] == null) {
                    throw settings.problem(p.key, "Missing property");
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }

    void generatePluralPropertyMethod(CharSink sink, PluralProperty p) throws IOException {
        sink.append("long amount").append(')');
        sink.begin();

        sink.append("int index = pluralForm(amount);");
        sink.ln();

        if (procResources.isSingle()) {
            sink.append("return ");
        } else {
            sink.append("return switch (localeTag)");
            sink.begin();
        }


        for (int localeTag = 0; localeTag < procResources.locales.size(); localeTag++) {
            var localeSettings = procResources.locales.get(localeTag);

            if (!procResources.isSingle()) {
                sink.append("case ").append(localeSettings.localeTag).append(" -> ");
            }

            sink.append("switch (index)");
            sink.begin();

            var pluralForms = p.messages[localeTag];

            for (int pluralForm = 0; pluralForm < pluralForms.length; pluralForm++) {
                var message = pluralForms[pluralForm];

                sink.append("case ").append(Integer.toString(pluralForm)).append(" -> ");
                printMessage(sink, message);

                sink.append(';');
                sink.ln();
            }

            sink.append("default -> throw new IllegalStateException();");
            sink.endsc();
        }

        if (!procResources.isSingle()) {
            sink.endsc();
        }

        sink.end();
    }

    void generateOrdinalPropertyMethod(CharSink sink, OrdinalProperty p) throws IOException {

        var referenceMessage = p.messages[REFERENCE_LOCALE_TAG];
        Arg[] sortedArgs = new Arg[referenceMessage.args.length];
        System.arraycopy(referenceMessage.args, 0, sortedArgs, 0, referenceMessage.args.length);
        Arrays.sort(sortedArgs, Comparator.comparingInt(c -> c.pos));
        for (int i = 0; i < sortedArgs.length; i++) {
            Arg arg = sortedArgs[i];
            sink.append(arg.type).append(" ").append(arg.name);

            if (i != sortedArgs.length - 1) {
                sink.append(", ");
            }
        }

        sink.append(')');
        sink.begin();

        if (procResources.isSingle()) {
            sink.append("return ");
        } else {
            sink.append("return switch (localeTag)");
            sink.begin();
        }

        for (int localeTag = 0; localeTag < procResources.locales.size(); localeTag++) {
            var localeSettings = procResources.locales.get(localeTag);

            if (!procResources.isSingle()) {
                sink.append("case ").append(localeSettings.localeTag).append(" -> ");
            }

            var message = p.messages[localeTag];
            printMessage(sink, message);

            sink.append(';');
            sink.ln();
        }

        if (!procResources.isSingle()) {
            sink.endsc();
        }


        sink.end();
    }

    void printMessage(CharSink sink, Message message) throws IOException {
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
    }

    void generateLocaleTagConstants(CharSink sink) throws IOException {
        sink.ln();
        sink.append("public enum LocaleTag implements io.github.skykatik.staticbundle.LocaleTag");
        sink.begin();

        for (int i = 0; i < procResources.locales.size(); i++) {
            var settings = procResources.locales.get(i);

            sink.append(settings.localeTag);
            sink.append('(');
            sink.append(constructLocale(settings.locale));
            sink.append(')');

            if (i != procResources.locales.size() - 1) {
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

    void generatePluralFormMethod(CharSink sink) throws IOException {
        sink.ln();

        sink.append("public int pluralForm(long value)");
        sink.begin();

        if (procResources.isSingle()) {
            sink.append("return ");
        } else {
            sink.append("return switch (localeTag)");
            sink.begin();
        }

        for (var settings : procResources.locales) {
            if (!procResources.isSingle()) {
                sink.append("case ").append(settings.localeTag).append(" -> ");
            }

            sink.append(settings.pluralFormFunction).append(';');
            sink.ln();
        }

        if (!procResources.isSingle()) {
            sink.endsc();
        }

        sink.end();
    }

    void generateWithLocaleTagMethod(CharSink sink) throws IOException {
        sink.ln();

        sink.append("public ").append(className).append(" withLocaleTag(LocaleTag localeTag)");
        sink.begin();

        if (procResources.isSingle()) {
            sink.append("return this;");
        } else {
            sink.append("if (this.localeTag == localeTag) return this;");
            sink.ln();
            sink.append("return new ").append(className).append("(localeTag);");
        }

        sink.end();
    }

    Bundle loadBundle(Locale locale) throws IOException {
        String localeTag = locale.equals(Locale.ROOT) ? "" : "_" + locale;

        String fileName = resourceFilenameFormat.replace("{locale}", localeTag);
        Path resourcePath = null;
        for (File resource : resources) {
            if (resource.getName().equals(fileName)) {
                resourcePath = resource.toPath();
                break;
            }
        }

        if (resourcePath == null) {
            throw new FileNotFoundException(fileName);
        }

        try (var reader = Files.newBufferedReader(resourcePath)) {
            var props = PropertiesReader.load(reader);
            return new Bundle(resourcePath, props);
        }
    }

    record Bundle(Path resourcePath, Map<String, String> properties) {
    }

    static String constructLocale(Locale locale) {
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

    static String translateKeyToMethodName(LocaleSettings settings, String key) {
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

        String methodName = new String(result, 0, d);
        if (!SourceVersion.isIdentifier(methodName) || SourceVersion.isKeyword(methodName)) {
            throw settings.problem(key, "Illegal name which translates into incorrect Java identifier");
        }

        return methodName;
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

    static final class LocaleSettings {
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
            this(locale, translateLocaleToLocaleTag(locale), localeTagValue, pluralFormsCount, pluralFormFunction);
        }

        IllegalStateException problem(String key, String text) {
            return new IllegalStateException("[Bundle: '" + relativeResourcePath + "', property: '" + key + "'] " + text);
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

    record ProcessingResources(List<LocaleSettings> locales) {

        boolean isSingle() {
            return locales.size() == 1;
        }
    }

    record Message(Arg[] args, String[] tokens) {

        static Message parse(LocaleSettings settings, String key, String text) {
            var tokens = new ArrayList<String>();
            var args = new ArrayList<Arg>();
            var matcher = ARGS.matcher(text);
            int argPos = 0;
            int prev = 0;
            while (matcher.find()) {
                tokens.add(makeLiteral(text.substring(prev, matcher.start())));

                String[] nameAndType = matcher.group(2).split(":");
                String name = nameAndType[0];
                String type = "String";
                if (nameAndType.length == 2) {
                    type = typeOf(nameAndType[1]);
                }

                String posStr = matcher.group(1);
                int pos;
                if (settings.localeTagValue == REFERENCE_LOCALE_TAG) {
                    if (posStr == null) {
                        throw settings.problem(key, "Non-indexed argument: '" + name + "'");
                    }

                    pos = Integer.parseInt(posStr.substring(0, posStr.length() - 1));

                    if (pos < 0) {
                        throw settings.problem(key, "Argument with negative index: '" + name + "'");
                    }
                } else {
                    if (posStr != null) {
                        throw settings.problem(key, "Mixed argument position: '" + name + "'");
                    }

                    pos = argPos++;
                }

                args.add(new Arg(pos, type, name));

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

    record Arg(int pos, String type, String name) {
    }

    record OrdinalProperty(String key, String methodName, Message[/*localeTag*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, String key, String text) {
            messages[settings.localeTagValue] = Message.parse(settings, key, text);
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
                throw settings.problem(key, "Aliases with plural property");
            }

            if (parts.pluralForm() >= settings.pluralFormsCount) {
                throw settings.problem(key, "Plural form is out of range [0, " + settings.pluralFormsCount + ")");
            }

            var msg = Message.parse(settings, key, text);
            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }

            locale[parts.pluralForm()] = msg;
        }
    }

    Property parseProperty(LocaleSettings settings, String key, String text) {
        var parts = PropertyKeyNaming.instance().parse(key);
        if (parts.pluralForm() != -1) {

            if (parts.pluralForm() < 0 || parts.pluralForm() >= settings.pluralFormsCount) {
                throw settings.problem(key, "Plural form is out of range [0, " + settings.pluralFormsCount + ")");
            }

            var msg = Message.parse(settings, key, text);
            var messages = new Message[procResources.locales.size()][];

            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }
            locale[parts.pluralForm()] = msg;

            return new PluralProperty(parts.baseKey(), translateKeyToMethodName(settings, parts.baseKey()), messages);
        }

        var msg = Message.parse(settings, key, text);
        var tokens = new Message[procResources.locales.size()];
        tokens[settings.localeTagValue] = msg;

        return new OrdinalProperty(parts.baseKey(), translateKeyToMethodName(settings, parts.baseKey()), tokens);
    }

    sealed interface Property {

        String key();

        String methodName();

        void merge(LocaleSettings settings, String key, String text);
    }
}
