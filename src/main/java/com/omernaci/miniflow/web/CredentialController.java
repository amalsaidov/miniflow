package com.omernaci.miniflow.web;

import com.omernaci.miniflow.common.i18n.Messages;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.credential.CredentialType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CredentialController {

    private static final String FIELD_PREFIX = "f_";

    private final CredentialService credentials;

    public CredentialController(CredentialService credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/credentials")
    public String list(Model model) {
        model.addAttribute("credentials", credentials.list());
        model.addAttribute("unreadable", credentials.unreadable());
        model.addAttribute("types", Arrays.asList(CredentialType.values()));
        model.addAttribute("knownTypes", Arrays.stream(CredentialType.values()).map(CredentialType::id).toList());
        return "credentials";
    }

    @GetMapping("/credentials/new")
    public String newForm(@RequestParam String type, Model model) {
        CredentialType t = CredentialService.type(type);
        Map<String, String> values = new LinkedHashMap<>();
        t.fields().forEach(f -> values.put(f.name(), f.defaultValue()));
        return form(model, null, "", t, values, Map.of());
    }

    @GetMapping("/credentials/{name}/edit")
    public String editForm(@PathVariable String name, Model model) {
        CredentialService.Summary s = credentials.summary(name)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, Messages.get("error.credential.notFound", name)));
        model.addAttribute("unreadable", !credentials.isReadable(name));
        return form(model, name, name, CredentialService.type(s.type()),
                credentials.publicValues(name), credentials.secretsPresent(name));
    }

    @PostMapping("/credentials")
    public String save(@RequestParam(required = false) String originalName,
                       @RequestParam String name,
                       @RequestParam String type,
                       @RequestParam Map<String, String> all,
                       Model model, RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<>();
        all.forEach((k, v) -> {
            if (k.startsWith(FIELD_PREFIX)) {
                values.put(k.substring(FIELD_PREFIX.length()), v);
            }
        });
        try {
            credentials.save(originalName, name, type, values);
        } catch (IllegalArgumentException e) {
            CredentialType t = CredentialService.type(type);
            Map<String, String> shown = new LinkedHashMap<>();
            t.fields().forEach(f -> shown.put(f.name(), f.secret() ? "" : values.getOrDefault(f.name(), "")));
            model.addAttribute("error", e.getMessage());
            Map<String, Boolean> present = originalName == null || originalName.isBlank()
                    ? Map.of() : credentials.secretsPresent(originalName);
            return form(model, blankToNull(originalName), name, t, shown, present);
        }
        ra.addFlashAttribute("success", Messages.get("cred.saved", name.trim()));
        return "redirect:/credentials";
    }

    @PostMapping("/credentials/{name}/delete")
    public String delete(@PathVariable String name, RedirectAttributes ra) {
        if (credentials.delete(name)) {
            ra.addFlashAttribute("success", Messages.get("cred.deleted", name));
        }
        return "redirect:/credentials";
    }

    private String form(Model model, String originalName, String name, CredentialType type,
                        Map<String, String> values, Map<String, Boolean> secretsPresent) {
        model.addAttribute("originalName", originalName);
        model.addAttribute("name", name);
        model.addAttribute("type", type);
        model.addAttribute("fields", type.fields());
        model.addAttribute("values", values);
        model.addAttribute("secretsPresent", secretsPresent);
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> hints = new LinkedHashMap<>();
        for (CredentialType.Field f : type.fields()) {
            String generic = "cred.field." + f.name();
            String specific = "cred.field." + type.id() + "." + f.name();
            labels.put(f.name(), Messages.getOrDefault(specific, Messages.getOrDefault(generic, f.name())));
            hints.put(f.name(), Messages.getOrDefault(specific + ".hint", Messages.getOrDefault(generic + ".hint", "")));
        }
        model.addAttribute("labels", labels);
        model.addAttribute("hints", hints);
        return "credential-form";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public static List<CredentialType> allTypes() {
        return Arrays.asList(CredentialType.values());
    }
}
