package io.github.skykatik.staticbundle.gen;

import io.github.skykatik.staticbundle.plugin.DefaultSourceSetSettings;
import io.github.skykatik.staticbundle.plugin.PropertyNaming;
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

public class StaticBundleProcessor {

    static final int REFERENCE_LOCALE_TAG = 0;
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

        var naming = sett.getNaming().get();
        procResources = new ProcessingResources(locales, naming);
    }

    public void validate() throws IOException {

        var referenceSettings = procResources.locales.get(REFERENCE_LOCALE_TAG);
        var referenceBundle = loadBundle(referenceSettings.locale);
        referenceSettings.relativeResourcePath = project.relativePath(referenceBundle.resourcePath);
        for (var e : referenceBundle.properties.entrySet()) {
            String key = e.getKey();
            String text = e.getValue();

            parseProperty(referenceSettings, key, text);
        }

        checkForMissingPluralForms(referenceSettings);
        checkForUnresolvedPropertyArgs(referenceSettings);

        for (int i = 1; i < procResources.locales.size(); i++) {
            var settings = procResources.locales.get(i);
            var bundle = loadBundle(settings.locale);
            settings.relativeResourcePath = bundle.resourcePath;

            for (var e : bundle.properties.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();

                var parts = procResources.naming.parse(k);
                var referenceProperty = properties.get(parts.baseKey());
                if (referenceProperty == null) {
                    throw settings.problem(k, "Extraneous property");
                }

                referenceProperty.merge(settings, parts, k, v);
            }

            checkForMissingPluralForms(settings);
            checkForUnresolvedPropertyArgs(settings);
        }
    }

    private void checkForUnresolvedPropertyArgs(LocaleSettings settings) {
        for (Property value : properties.values()) {
            if (value instanceof PluralProperty p) {
                var pluralForms = p.messages[settings.localeTagValue];
                for (int n = 0; n < pluralForms.length; n++) {
                    var pluralForm = pluralForms[n];
                    String key = procResources.naming.format(p.key, n);
                    for (Arg arg : pluralForm.args) {
                        if (arg instanceof PropertyArg pa) {
                            checkForUnresolvedPropertyArg0(settings, p, key, pa);
                        }
                    }
                }
            } else if (value instanceof OrdinalProperty p) {
                var message = p.messages[settings.localeTagValue];
                for (Arg arg : message.args) {
                    if (arg instanceof PropertyArg pa) {
                        checkForUnresolvedPropertyArg0(settings, p, p.key, pa);
                    }
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }

    private void checkForUnresolvedPropertyArg0(LocaleSettings settings, Property p, String key, PropertyArg pa) {
        var property = properties.get(pa.baseKey());
        if (property == null) {
            throw settings.problem(key, "No property '" + pa.baseKey() + "' found");
        }

        if (property == p) {
            throw settings.problem(key, "Recursive property argument");
        }

        if (property instanceof PluralProperty o) {
            if (!(pa instanceof PluralPropertyArg pp)) {
                throw settings.problem(key, "Property '" + o.key + "' is plural, but argument consider it as ordinal");
            }

            int i = p.argTable().index(pp.amountArg);
            if (i == -1) {
                throw settings.problem(key, "Unknown amount argument '" + pp.amountArg + "' for plural property '" + o.key + "'");
            }

            pp.methodName = o.methodName;
        } else if (property instanceof OrdinalProperty o) {
            if (!(pa instanceof OrdinalPropertyArg pp)) {
                throw settings.problem(key, "Property '" + o.key + "' is ordinal, but argument consider it as plural");
            }

            if (o.argTable.size() != pp.propertyArgs.length) {
                throw settings.problem(key, "Property '" + o.key + "' requires '" + o.argTable.size() + "'" +
                        " arguments, but passed '" + pp.propertyArgs.length + "'");
            }

            for (String propertyArg : pp.propertyArgs) {
                int i = p.argTable().index(propertyArg);
                if (i == -1) {
                    throw settings.problem(key, "Unknown argument '" + propertyArg + "' for property '" + o.key + "'");
                }
            }

            pp.methodName = o.methodName;
        } else {
            throw new IllegalStateException();
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
                    """);
            if (!className.equals("MessageSource")) {
                sink.append("import io.github.skykatik.staticbundle.MessageSource;").ln();
            }
            sink.ln();

            sink.append("public final class ").append(className).append(" extends ");
            if (className.equals("MessageSource")) {
                sink.append("io.github.skykatik.staticbundle.");
            }
            sink.append("MessageSource");

            sink.begin();

            sink.append("public final LocaleTag localeTag;");
            sink.ln();

            sink.ln();
            sink.append("public ").append(className).append("(LocaleTag localeTag)");
            sink.begin();
            sink.append("this.localeTag = Objects.requireNonNull(localeTag);");
            sink.end();

            sink.ln();
            sink.append("@Override").ln();
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
                        String key = procResources.naming.format(p.key, n);
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
        var parameters = Arrays.stream(referenceMessage.args)
                .<ParameterArg>mapMulti((arg, consumer) -> {
                    if (arg instanceof ParameterArg pa) {
                        consumer.accept(pa);
                    }
                })
                .sorted(Comparator.comparingInt(c -> c.pos))
                .toList();
        for (int i = 0; i < parameters.size(); i++) {
            var parameterArg = parameters.get(i);
            sink.append(parameterArg.type).append(" ").append(parameterArg.name);

            if (i != parameters.size() - 1) {
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
                if (arg instanceof CodeArg c) {
                    sink.append(c.code);
                } else if (arg instanceof OrdinalPropertyArg p) {
                    sink.append(p.methodName);
                    sink.append('(');
                    for (int v = 0; v < p.propertyArgs.length; v++) {
                        sink.append(p.propertyArgs[v]);
                        if (v != p.propertyArgs.length - 1) {
                            sink.append(", ");
                        }
                    }
                    sink.append(')');
                } else if (arg instanceof PluralPropertyArg p) {
                    sink.append(p.methodName);
                    sink.append('(');
                    sink.append(p.amountArg);
                    sink.append(')');
                } else if (arg instanceof DefaultArg p) {
                    sink.append(p.name);
                } else if (arg instanceof ParameterArg p) {
                    sink.append(p.name);
                } else {
                    throw new IllegalStateException();
                }
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

        sink.append("public final Locale locale;");
        sink.ln(2);

        sink.append("LocaleTag(Locale locale)");
        sink.begin();
        sink.append("this.locale = locale;");
        sink.end();

        sink.ln();
        sink.append("@Override").ln();
        sink.append("public Locale locale()");
        sink.begin();
        sink.append("return locale;");
        sink.end();

        sink.end();
    }

    void generatePluralFormMethod(CharSink sink) throws IOException {
        sink.ln();

        sink.append("@Override").ln();
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

        String relative = project.relativePath(resourcePath);
        try (var reader = Files.newBufferedReader(resourcePath)) {
            var props = PropertiesReader.load(relative, reader);
            return new Bundle(relative, props);
        }
    }

    record Bundle(String resourcePath, Map<String, String> properties) {
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

    String translateKeyToMethodName(LocaleSettings settings, String key) {
        String methodName = procResources.naming.toMethodName(key);

        if (!isValidJavaIdentifier(methodName)) {
            throw settings.problem(key, "Naming '" + procResources.naming +
                    "' generated illegal method name: '" + methodName + "'");
        }

        return methodName;
    }

    static boolean isValidJavaIdentifier(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
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

    static String translateLocaleToTag(Locale locale) {
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
            this(locale, translateLocaleToTag(locale), localeTagValue, pluralFormsCount, pluralFormFunction);
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

    record ProcessingResources(List<LocaleSettings> locales, PropertyNaming naming) {

        boolean isSingle() {
            return locales.size() == 1;
        }
    }

    record Message(Arg[] args, String[] tokens) {

        static Message parse(LocaleSettings settings, ArgTable argTable, String key, String text) {
            var tokens = new ArrayList<String>();
            var args = new ArrayList<Arg>();
            int prev = 0;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                if (c == '{') {
                    char p = i - 1 >= 0 ? text.charAt(i - 1) : '\0';
                    int start = i + 1;
                    int end = indexOfUnescaped(text, i, '}');
                    if (end == -1) {
                        continue;
                    }

                    String argText = text.substring(start, end);
                    if (argText.isEmpty()) {
                        continue;
                    }

                    boolean call = p == '$';
                    boolean propertyCall = p == '#';

                    Arg arg;
                    if (call) {
                        arg = new CodeArg(argText);
                    } else if (propertyCall) {
                        arg = parsePropertyArg(settings, argTable, key, argText);
                    } else {
                        arg = parseParameterArg(settings, argTable, key, argText);
                    }

                    if (arg == null) {
                        continue;
                    }

                    int begin = call || propertyCall ? i - 1 : i;
                    tokens.add(makeLiteral(text.substring(prev, begin)));

                    args.add(arg);
                    prev = end + 1;
                }
            }
            if (prev != text.length()) {
                tokens.add(makeLiteral(text.substring(prev)));
            }

            if (settings.localeTagValue == REFERENCE_LOCALE_TAG) {
                argTable.trim();
                for (int i = 0; i < argTable.names.length; i++) {
                    if (argTable.names[i] == null) {
                        throw settings.problem(key, "No argument for index '" + i + "'");
                    }
                }
            }

            return new Message(args.toArray(EMPTY_ARG_ARRAY), tokens.toArray(EMPTY_STRING_ARRAY));
        }

        static Arg parsePropertyArg(LocaleSettings settings, ArgTable argTable,
                                    String key, String argText) {

            for (int i = 0; i < argText.length(); i++) {
                char c = argText.charAt(i);

                if (c == '(') {
                    int end = indexOfUnescaped(argText, i, ')');
                    if (end == -1 || end != argText.length() - 1) {
                        return null;
                    }

                    String baseKey = argText.substring(0, i);
                    var propertyArgs = new ArrayList<String>();
                    int prev = i + 1;
                    for (int k = i + 1; k < end; k++) {
                        c = argText.charAt(k);
                        if (Character.isWhitespace(c)) {
                            prev = k + 1;
                            continue;
                        }

                        if (c == ',') {
                            propertyArgs.add(argText.substring(prev, k));
                            prev = k + 1;
                        }
                    }
                    if (prev != end) {
                        propertyArgs.add(argText.substring(prev, end));
                    }

                    return new OrdinalPropertyArg(baseKey, propertyArgs.toArray(EMPTY_STRING_ARRAY));
                } else if (c == '[') {
                    int end = indexOfUnescaped(argText, i, ']');
                    if (end == -1 || end != argText.length() - 1) {
                        return null;
                    }

                    String baseKey = argText.substring(0, i);
                    String amountArg = argText.substring(i + 1, end);
                    return new PluralPropertyArg(baseKey, amountArg);
                }
            }
            return new OrdinalPropertyArg(argText, EMPTY_STRING_ARRAY);
        }

        static int indexOfUnescaped(String text, int base, char e) {
            for (int i = base; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i++;
                    continue;
                }

                if (c == e) {
                    return i;
                }
            }
            return -1;
        }

        static Arg parseParameterArg(LocaleSettings settings, ArgTable argTable,
                                     String key, String argText) {
            if (settings.localeTagValue == REFERENCE_LOCALE_TAG) {
                String[] parts = argText.split(":");
                if (parts.length < 2) {
                    return null;
                }
                String posStr = parts[0];
                String name = parts[1];
                int pos;
                try {
                    pos = Integer.parseInt(posStr);
                } catch (IllegalArgumentException e) {
                    return null;
                }

                if (pos < 0) {
                    throw settings.problem(key, "Argument with negative index: '" + name + "'");
                }

                if (!isValidJavaIdentifier(name)) {
                    throw settings.problem(key, "Argument with illegal name: '" + name + "'");
                }

                int occupiedIndex = argTable.index(name);
                if (occupiedIndex != -1) {
                    throw settings.problem(key, "Argument with name: '" +
                            name + "' reuses name of index '" + occupiedIndex + "'");
                }

                String occupiedName = argTable.add(pos, name);
                if (occupiedName != null) {
                    throw settings.problem(key, "Argument with name: '" +
                            name + "' reuses index of '" + occupiedName + "'");
                }

                String type = "String";
                if (parts.length >= 3) {
                    type = parts[2];
                }

                return new ParameterArg(pos, type, name);
            }

            String name;
            int pos;
            try {
                pos = Integer.parseInt(argText);
                if (pos < 0) {
                    throw settings.problem(key, "Argument with negative index: '" + argText + "'");
                }
                name = argTable.name(pos);
                if (argTable.isEmpty()) {
                    throw settings.problem(key, "Extraneous argument with index: '" + argText + "'");
                }
                if (name == null) {
                    throw settings.problem(key, "Argument index is out of range: [0, " + argTable.size() + ")");
                }
            } catch (IllegalArgumentException e) {
                name = argText;
                if (argTable.index(name) == -1) {
                    throw settings.problem(key, "Argument with unknown name: '" + name + "'");
                }
            }

            if (name != null && !isValidJavaIdentifier(name)) {
                throw settings.problem(key, "Argument with illegal name: '" + name + "'");
            }
            return new DefaultArg(name);
        }

        static final Arg[] EMPTY_ARG_ARRAY = new Arg[0];

        @Override
        public String toString() {
            return "Message{" +
                    "args=" + Arrays.toString(args) +
                    ", messages=" + Arrays.toString(tokens) +
                    '}';
        }
    }

    record CodeArg(String code) implements Arg {
    }

    sealed interface PropertyArg extends Arg {
        String baseKey();

        String methodName();
    }

    static final class PluralPropertyArg implements PropertyArg {
        final String baseKey;
        final String amountArg;

        String methodName;

        PluralPropertyArg(String baseKey, String amountArg) {
            this.baseKey = baseKey;
            this.amountArg = amountArg;
        }

        @Override
        public String baseKey() {
            return baseKey;
        }

        @Override
        public String methodName() {
            return methodName;
        }

        @Override
        public String toString() {
            return "PluralPropertyArg{" +
                    "baseKey='" + baseKey + '\'' +
                    ", amountArg='" + amountArg + '\'' +
                    ", methodName='" + methodName + '\'' +
                    '}';
        }
    }

    static final class OrdinalPropertyArg implements PropertyArg {
        final String baseKey;
        final String[] propertyArgs;

        String methodName;

        OrdinalPropertyArg(String baseKey, String[] propertyArgs) {
            this.baseKey = baseKey;
            this.propertyArgs = propertyArgs;
        }

        @Override
        public String baseKey() {
            return baseKey;
        }

        @Override
        public String methodName() {
            return methodName;
        }

        @Override
        public String toString() {
            return "OrdinalPropertyArg{" +
                    "baseKey='" + baseKey + '\'' +
                    ", propertyArgs=" + Arrays.toString(propertyArgs) +
                    ", methodName='" + methodName + '\'' +
                    '}';
        }
    }

    record ParameterArg(int pos, String type, String name) implements Arg {
    }

    record DefaultArg(String name) implements Arg {
    }

    sealed interface Arg {
    }

    record OrdinalProperty(String key, String methodName,
                           ArgTable argTable,
                           Message[/*localeTag*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, PropertyNaming.Parts parts, String key, String text) {
            messages[settings.localeTagValue] = Message.parse(settings, argTable, key, text);
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
                          ArgTable argTable,
                          Message[/*localeTag*/][/*pluralForm*/] messages) implements Property {
        @Override
        public void merge(LocaleSettings settings, PropertyNaming.Parts parts, String key, String text) {
            if (parts.pluralForm() == -1) {
                throw settings.problem(key, "Aliases with plural property");
            }

            if (parts.pluralForm() >= settings.pluralFormsCount) {
                throw settings.problem(key, "Plural form is out of range [0, " + settings.pluralFormsCount + ")");
            }

            var msg = Message.parse(settings, argTable, key, text);
            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }

            locale[parts.pluralForm()] = msg;
        }
    }

    Property parseProperty(LocaleSettings settings, PropertyNaming.Parts parts, String key, String text) {

        if (parts.pluralForm() != -1) {
            if (parts.pluralForm() < 0 || parts.pluralForm() >= settings.pluralFormsCount) {
                throw settings.problem(key, "Plural form is out of range [0, " + settings.pluralFormsCount + ")");
            }

            var argNameTable = new ArgTable();
            var msg = Message.parse(settings, argNameTable, key, text);
            var messages = new Message[procResources.locales.size()][];

            var locale = messages[settings.localeTagValue];
            if (locale == null) {
                messages[settings.localeTagValue] = locale = new Message[settings.pluralFormsCount];
            }
            locale[parts.pluralForm()] = msg;

            String methodName = translateKeyToMethodName(settings, parts.baseKey());
            return new PluralProperty(parts.baseKey(), methodName, argNameTable, messages);
        }

        var argNameTable = new ArgTable();
        var msg = Message.parse(settings, argNameTable, key, text);
        var tokens = new Message[procResources.locales.size()];
        tokens[settings.localeTagValue] = msg;

        String methodName = translateKeyToMethodName(settings, key);
        return new OrdinalProperty(parts.baseKey(), methodName, argNameTable, tokens);
    }

    void parseProperty(LocaleSettings settings, String key, String text) {
        var parts = procResources.naming.parse(key);
        properties.compute(parts.baseKey(), (k, v) -> {
            if (v == null) {
                return parseProperty(settings, parts, key, text);
            }

            v.merge(settings, parts, key, text);
            return v;
        });
    }

    sealed interface Property {

        String key();

        String methodName();

        ArgTable argTable();

        void merge(LocaleSettings settings, PropertyNaming.Parts parts, String key, String text);
    }

    static class ArgTable {
        int count;
        String[] names = EMPTY_STRING_ARRAY;

        String add(int index, String name) {
            if (index >= names.length) {
                names = Arrays.copyOf(names, names.length + 4);
            }
            String currentName = names[index];
            if (currentName == null) {
                names[index] = name;
                count++;
                return null;
            }
            return currentName;
        }

        String name(int index) {
            return names[index];
        }

        int index(String name) {
            for (int i = 0; i < count; i++) {
                if (name.equals(names[i])) {
                    return i;
                }
            }
            return -1;
        }

        int maxIndex() {
            return names.length;
        }

        boolean isEmpty() {
            return names == EMPTY_STRING_ARRAY;
        }

        int size() {
            return count;
        }

        public void trim() {
            names = Arrays.copyOf(names, count);
        }
    }

    static final String[] EMPTY_STRING_ARRAY = new String[0];
}
