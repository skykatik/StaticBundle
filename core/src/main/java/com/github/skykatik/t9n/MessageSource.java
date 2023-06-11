package com.github.skykatik.t9n;

public abstract class MessageSource {
    protected final int localeTag;

    protected MessageSource(int localeTag) {
        this.localeTag = localeTag;
    }

    public final int localeTag() {
        return localeTag;
    }
}
