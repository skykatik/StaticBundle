package io.github.skykatik.staticbundle.test;

public class Main {
    public static void main(String[] args) {
        var msg = new CustomMessageSource(CustomMessageSource.LocaleTag.ROOT);
        System.out.println(msg.commandsTestMessage("reason", "123"));
    }
}
