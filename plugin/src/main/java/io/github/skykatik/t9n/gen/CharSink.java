package io.github.skykatik.t9n.gen;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

class CharSink implements Closeable {

    public static final String DEFAULT_INDENT = "     ";
    public static final int DEFAULT_LINE_WRAP = 120;
    public static final int LINE_WRAP_INDENT = 2;

    private final WriterWrapper writer;
    private final String autoIndent;
    private final int lineWrap;

    private int column; // 0-based
    private int indentLevel;
    private boolean indent;

    public CharSink(Writer writer) {
        this(writer, DEFAULT_INDENT, DEFAULT_LINE_WRAP);
    }

    public CharSink(Writer writer, String autoIndent, int lineWrap) {
        this.writer = new WriterWrapper(writer);
        this.autoIndent = autoIndent;
        this.lineWrap = lineWrap;
    }

    private void updateLocation(int columnAdd) {
        column += columnAdd;
        if (writer.lastChar == '\n') {
            column = 0;

            if (indentLevel != 0) {
                indent = true;
            }
        }
    }

    private void appendIndentIfNeed() throws IOException {
        if (indent) {
            String str = autoIndent.repeat(indentLevel);
            writer.write(str);
            column = str.length();
            indent = false;
        }
    }

    public CharSink incIndent() {
        return incIndent(1);
    }

    public int indentLevel() {
        return indentLevel;
    }

    public CharSink incIndent(int count) {
        indentLevel += count;
        return this;
    }

    public CharSink decIndent() {
        return decIndent(1);
    }

    public CharSink decIndent(int count) {
        indentLevel -= count;
        if (indentLevel < 0)
            throw new IllegalStateException();
        return this;
    }

    public CharSink ln(int count) throws IOException {
        writer.write("\n".repeat(count));
        column = 0;
        if (indentLevel != 0) {
            indent = true;
        }

        return this;
    }

    public CharSink ln() throws IOException {
        writer.write('\n');
        column = 0;
        if (indentLevel != 0) {
            indent = true;
        }

        return this;
    }

    public CharSink lno() throws IOException {
        if (writer.lastChar != '\n') {
            ln();
        }
        return this;
    }

    public CharSink lb() throws IOException {
        indentLevel += LINE_WRAP_INDENT;
        ln();
        appendIndentIfNeed();
        indentLevel -= LINE_WRAP_INDENT;
        return this;
    }

    public CharSink lw() throws IOException {
        if (column >= lineWrap) {
            lb();
        }
        return this;
    }

    public CharSink begin() throws IOException {
        incIndent();
        if (writer.lastChar != ' ') {
            append(' ');
        }
        append('{');
        ln();
        return this;
    }

    public CharSink endsc() throws IOException {
        decIndent();
        lno();
        append("};");
        ln();
        return this;
    }

    public CharSink end() throws IOException {
        decIndent();
        lno();
        append('}');
        ln();
        return this;
    }

    public CharSink append(char c) throws IOException {
        appendIndentIfNeed();
        writer.write(c);
        updateLocation(1);
        return this;
    }

    public CharSink append(String str) throws IOException {
        if (str.isEmpty()) {
            return this;
        }

        appendIndentIfNeed();
        writer.write(str);
        updateLocation(str.length());
        return this;
    }

    public CharSink clw() throws IOException {
        if (column >= lineWrap) {
            lb();
        } else {
            append(' ');
        }
        return this;
    }

    public Writer writer() {
        return writer.writer;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    static class WriterWrapper {
        final Writer writer;

        int lastChar;

        WriterWrapper(Writer writer) {
            this.writer = writer;
        }

        public void write(int c) throws IOException {
            writer.write(c);
            lastChar = c;
        }

        public void write(char[] cbuf) throws IOException {
            writer.write(cbuf);
            if (cbuf.length > 0)
                lastChar = cbuf[cbuf.length - 1];
        }

        public void write(char[] cbuf, int off, int len) throws IOException {
            writer.write(cbuf, off, len);
            if (len > 0)
                lastChar = cbuf[len - 1];
        }

        public void write(String str) throws IOException {
            writer.write(str);
            if (!str.isEmpty())
                lastChar = str.charAt(str.length() - 1);
        }

        public void write(String str, int off, int len) throws IOException {
            writer.write(str, off, len);
            if (len > 0)
                lastChar = str.charAt(len - 1);
        }

        public void flush() throws IOException {
            writer.flush();
        }

        public void close() throws IOException {
            writer.close();
        }
    }
}
