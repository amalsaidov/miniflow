package com.omernaci.miniflow.scheduler;

public final class CronSupport {

    private CronSupport() {
    }

    public static String normalize(String expr) {
        String trimmed = expr.trim();
        return trimmed.split("\\s+").length == 5 ? "0 " + trimmed : trimmed;
    }
}
