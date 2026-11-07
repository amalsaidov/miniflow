package com.omernaci.miniflow.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class Messages {

    private static volatile MessageSource source;

    public Messages(MessageSource messageSource) {
        source = messageSource;
    }

    public static String get(String code, Object... args) {
        return get(LocaleContextHolder.getLocale(), code, args);
    }

    public static String get(Locale locale, String code, Object... args) {
        MessageSource s = source;
        if (s == null) {
            return args.length == 0 ? code : code + " " + Arrays.toString(args);
        }
        return s.getMessage(code, args, code, locale);
    }

    public static String getOrDefault(String code, String defaultValue) {
        MessageSource s = source;
        return s == null ? defaultValue : s.getMessage(code, null, defaultValue, LocaleContextHolder.getLocale());
    }
}
