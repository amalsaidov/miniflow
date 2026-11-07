package com.omernaci.miniflow.node.support;

import com.omernaci.miniflow.common.exception.MiniFlowException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class Params {

    private Params() {
    }

    public static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return "null".equals(s) ? null : s;
    }

    public static String str(Map<String, Object> params, String key, String defaultValue) {
        String s = str(params, key);
        return s == null || s.isBlank() ? defaultValue : s;
    }

    public static String required(Map<String, Object> params, String key) {
        String s = str(params, key);
        if (s == null || s.isBlank()) {
            throw new MiniFlowException("error.param.required", key);
        }
        return s;
    }

    public static int integer(Map<String, Object> params, String key, int defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new MiniFlowException("error.param.number", key, v);
        }
    }

    public static Double decimal(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new MiniFlowException("error.param.number", key, v);
        }
    }

    public static boolean bool(Map<String, Object> params, String key, boolean defaultValue) {
        Object v = params.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    public static List<String> stringList(Object param) {
        List<String> out = new ArrayList<>();
        if (param instanceof Collection<?> list) {
            list.forEach(item -> {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            });
        } else if (param != null && !String.valueOf(param).isBlank()) {
            for (String item : String.valueOf(param).split(",")) {
                if (!item.isBlank()) {
                    out.add(item.trim());
                }
            }
        }
        return out;
    }
}
