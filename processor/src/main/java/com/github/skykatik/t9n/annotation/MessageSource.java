package com.github.skykatik.t9n.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface MessageSource {

    String className();

    String baseName();

    LocaleSettings[] settings();
}
