package com.omernaci.miniflow.engine.expression;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.TypedValue;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Helper functions available at the top level of an expression:
 * {@code join}, {@code default}, {@code format}, {@code round}, {@code sum}, {@code count} and {@code json}.
 * They have no side effects and no access to files, network or classes.
 */
final class ExpressionFunctions implements MethodResolver {

    static final Set<String> NAMES = Set.of("join", "default", "format", "round", "sum", "count", "json");

    private static final ObjectMapper JSON = new ObjectMapper();

    static final class Scope extends LinkedHashMap<String, Object> {
    }

    @Override
    public MethodExecutor resolve(EvaluationContext context, Object target, String name,
                                  List<TypeDescriptor> argumentTypes) {
        if (!(target instanceof Scope) || !NAMES.contains(name)) {
            return null;
        }
        return (ctx, t, args) -> {
            try {
                return new TypedValue(call(name, args));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new AccessException(name + "(): " + e.getMessage(), e);
            }
        };
    }

    static Object call(String name, Object[] args) {
        return switch (name) {
            case "join" -> join(arg(args, 0, name, 1, 2), args.length > 1 ? args[1] : ", ");
            case "default" -> {
                arg(args, 0, name, 2, 2);
                Object v = args[0];
                yield v == null || (v instanceof CharSequence cs && cs.toString().isBlank())
                        || (v instanceof Collection<?> c && c.isEmpty()) ? args[1] : v;
            }
            case "format" -> format(arg(args, 0, name, 2, 2), String.valueOf(args[1]));
            case "round" -> round(arg(args, 0, name, 1, 2), args.length > 1 ? toNumber(args[1]).intValue() : 0);
            case "sum" -> sum(arg(args, 0, name, 1, 1));
            case "count" -> {
                Object v = arg(args, 0, name, 1, 1);
                yield v == null ? 0 : v instanceof Collection<?> c ? c.size() : v instanceof Map<?, ?> m ? m.size()
                        : v instanceof CharSequence cs ? cs.length() : 1;
            }
            case "json" -> {
                try {
                    yield JSON.writeValueAsString(arg(args, 0, name, 1, 1));
                } catch (Exception e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
            default -> throw new IllegalArgumentException("unknown function " + name);
        };
    }

    private static Object arg(Object[] args, int i, String name, int min, int max) {
        if (args.length < min || args.length > max) {
            throw new IllegalArgumentException("expects " + (min == max ? String.valueOf(min) : min + "-" + max)
                    + " argument(s), got " + args.length);
        }
        return args[i];
    }

    private static String join(Object list, Object separator) {
        if (list == null) {
            return "";
        }
        if (!(list instanceof Collection<?> items)) {
            return String.valueOf(list);
        }
        StringJoiner j = new StringJoiner(separator == null ? "" : String.valueOf(separator));
        items.forEach(i -> j.add(i == null ? "" : String.valueOf(i)));
        return j.toString();
    }

    private static String format(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern(pattern);
        TemporalAccessor t;
        if (value instanceof TemporalAccessor ta && !(value instanceof Instant)) {
            t = ta;
        } else if (value instanceof Instant i) {
            t = LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        } else if (value instanceof Date d) {
            t = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        } else if (value instanceof Number n) {
            t = LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault());
        } else {
            String s = String.valueOf(value).trim().replace(' ', 'T');
            t = parseIso(s);
        }
        return f.format(t);
    }

    private static TemporalAccessor parseIso(String s) {
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeException e1) {
            try {
                return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeException e2) {
                try {
                    return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDateTime();
                } catch (DateTimeException e3) {
                    return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
                }
            }
        }
    }

    private static Number round(Object value, int digits) {
        if (value == null) {
            return null;
        }
        BigDecimal r = new BigDecimal(String.valueOf(toNumber(value))).setScale(Math.max(digits, 0), RoundingMode.HALF_UP);
        return digits <= 0 ? (Number) r.longValue() : (Number) r.doubleValue();
    }

    private static Number sum(Object list) {
        if (list == null) {
            return 0;
        }
        List<Object> items = list instanceof Collection<?> c ? new ArrayList<>(c) : List.of(list);
        BigDecimal total = BigDecimal.ZERO;
        boolean decimal = false;
        for (Object i : items) {
            if (i == null || (i instanceof CharSequence cs && cs.toString().isBlank())) {
                continue;
            }
            Number n = toNumber(i);
            decimal |= !(n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte
                    || n instanceof BigInteger);
            total = total.add(new BigDecimal(String.valueOf(n)));
        }
        return decimal ? (Number) total.doubleValue() : (Number) total.longValue();
    }

    private static Number toNumber(Object v) {
        if (v instanceof Number n) {
            return n;
        }
        String s = String.valueOf(v).trim();
        try {
            return s.contains(".") || s.contains("e") || s.contains("E") ? Double.parseDouble(s) : Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + s);
        }
    }
}
