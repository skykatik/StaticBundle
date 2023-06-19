package test;

public class Main {
    public static void main(String[] args) {
        SuperMsgSource s = new SuperMsgSource(SuperMsgSource.LocaleTag.ROOT);
        System.out.println(s.key());
    }
}
