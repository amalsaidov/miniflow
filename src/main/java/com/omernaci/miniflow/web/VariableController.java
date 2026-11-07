package com.omernaci.miniflow.web;

import com.omernaci.miniflow.common.i18n.Messages;
import com.omernaci.miniflow.variable.VariableService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class VariableController {

    private final VariableService variables;

    public VariableController(VariableService variables) {
        this.variables = variables;
    }

    public record Form(String originalName, String name, String value, String description) {
    }

    @GetMapping("/variables")
    public String list(@RequestParam(required = false) String edit, Model model) {
        List<VariableService.Row> rows = variables.list();
        model.addAttribute("rows", rows);
        if (!model.containsAttribute("form")) {
            Form form = new Form("", "", "", "");
            if (edit != null) {
                form = rows.stream().filter(r -> r.name().equals(edit)).findFirst()
                        .map(r -> new Form(r.fromDb() ? r.name() : "", r.name(), r.value(),
                                r.description() == null ? "" : r.description()))
                        .orElse(form);
            }
            model.addAttribute("form", form);
        }
        return "variables";
    }

    @PostMapping("/variables")
    public String save(@RequestParam(required = false) String originalName,
                       @RequestParam String name,
                       @RequestParam(required = false) String value,
                       @RequestParam(required = false) String description,
                       RedirectAttributes ra) {
        try {
            variables.save(originalName, name, value, description);
            ra.addFlashAttribute("success", Messages.get("var.saved", name.trim()));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("form", new Form(originalName == null ? "" : originalName, name,
                    value == null ? "" : value, description == null ? "" : description));
        }
        return "redirect:/variables";
    }

    @PostMapping("/variables/{name}/delete")
    public String delete(@PathVariable String name, RedirectAttributes ra) {
        if (variables.delete(name)) {
            ra.addFlashAttribute("success", Messages.get("var.deleted", name));
        }
        return "redirect:/variables";
    }
}
