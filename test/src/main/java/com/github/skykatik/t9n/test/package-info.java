@MessageSource(
        className = "CustomMessageSource",
        baseName = "resources.messages",
        settings = {
                @LocaleSettings(locale = "", pluralForms = 4, pluralFunction = "value == 1 ? 3 : value % 10 == 1 && value % 100 != 11 ? 0 : value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20) ? 1 : 2"),
                @LocaleSettings(locale = "en", pluralForms = 2, pluralFunction = "value == 1 ? 0 : 1")
        }
)
package com.github.skykatik.t9n.test;

import com.github.skykatik.t9n.annotation.LocaleSettings;
import com.github.skykatik.t9n.annotation.MessageSource;
