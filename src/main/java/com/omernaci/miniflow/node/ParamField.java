package com.omernaci.miniflow.node;

import com.omernaci.miniflow.common.i18n.Messages;

import java.util.List;

/**
 * Describes one node parameter for the form in the editor.
 *
 * <p>Kinds: {@code text}, {@code textarea}, {@code code}, {@code number}, {@code boolean},
 * {@code select}, {@code credential}, {@code keyvalue}, {@code list}, {@code json} and
 * {@code slackBlocks}. {@code showIf} has the form {@code "operation=createIssue,comment"}.
 * Labels and hints come from {@code node.<type>.field.<name>} or {@code field.<name>} messages.
 */
public record ParamField(String name, String kind, boolean required, List<String> options,
                         String placeholder, String showIf, String label, String hint) {

    public static ParamField of(String name, String kind) {
        return new ParamField(name, kind, false, List.of(), null, null, null, null);
    }

    public static ParamField text(String name) {
        return of(name, "text");
    }

    public static ParamField textarea(String name) {
        return of(name, "textarea");
    }

    public static ParamField code(String name) {
        return of(name, "code");
    }

    public static ParamField number(String name) {
        return of(name, "number");
    }

    public static ParamField bool(String name) {
        return of(name, "boolean");
    }

    public static ParamField select(String name, String... options) {
        return new ParamField(name, "select", false, List.of(options), null, null, null, null);
    }

    public static ParamField credential() {
        return of("credential", "credential").mandatory();
    }

    public static ParamField keyValue(String name) {
        return of(name, "keyvalue");
    }

    public static ParamField list(String name) {
        return of(name, "list");
    }

    public static ParamField json(String name) {
        return of(name, "json");
    }

    public ParamField mandatory() {
        return new ParamField(name, kind, true, options, placeholder, showIf, label, hint);
    }

    public ParamField placeholder(String text) {
        return new ParamField(name, kind, required, options, text, showIf, label, hint);
    }

    public ParamField when(String param, String... values) {
        return new ParamField(name, kind, required, options, placeholder,
                param + "=" + String.join(",", values), label, hint);
    }

    public ParamField localized(String nodeType) {
        String specific = "node." + nodeType + ".field." + name;
        String generic = "field." + name;
        String l = Messages.getOrDefault(specific, Messages.getOrDefault(generic, humanize(name)));
        String h = Messages.getOrDefault(specific + ".hint", Messages.getOrDefault(generic + ".hint", ""));
        return new ParamField(name, kind, required, options, placeholder, showIf, l, h);
    }

    private static String humanize(String name) {
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return spaced.isEmpty() ? name : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
