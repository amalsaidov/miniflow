package com.omernaci.miniflow.node.notify;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.Params;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class EmailNode implements NodeExecutor {

    private final CredentialService credentials;

    public EmailNode(CredentialService credentials) {
        this.credentials = credentials;
    }

    @Override
    public String type() {
        return "email";
    }

    @Override
    public String label() {
        return "Email";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("smtp");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("to", "someone@example.com");
        p.put("subject", "MiniFlow notification");
        p.put("text", "Hello, {{ input }}");
        p.put("html", false);
        p.put("attachments", "");
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.text("to").mandatory().placeholder("someone@example.com"),
                ParamField.text("cc"),
                ParamField.text("subject").mandatory(),
                ParamField.textarea("text").mandatory(),
                ParamField.bool("html"),
                ParamField.text("attachments").placeholder("{{ input.file }}"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "smtp");
        String to = Params.required(params, "to");
        String subject = Params.str(params, "subject", "");
        String text = Params.str(params, "text", "");
        boolean html = Params.bool(params, "html", false);
        List<Attachment> attachments = attachments(params.get("attachments"));

        JavaMailSenderImpl sender = sender(c);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(), "UTF-8");
            helper.setFrom(c.get("from"));
            helper.setTo(split(to));
            String cc = Params.str(params, "cc");
            if (cc != null && !cc.isBlank()) {
                helper.setCc(split(cc));
            }
            helper.setSubject(subject);
            helper.setText(text, html);
            for (Attachment a : attachments) {
                helper.addAttachment(a.name(), new ByteArrayResource(a.bytes()), a.mimeType());
            }
            sender.send(message);
        } catch (MessagingException | MailException e) {
            throw new MiniFlowException(e, "error.email", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sent", true);
        out.put("to", to);
        out.put("subject", subject);
        out.put("attachments", attachments.stream().map(Attachment::name).toList());
        return NodeResult.of(out);
    }

    private record Attachment(String name, String mimeType, byte[] bytes) {
    }

    private static List<Attachment> attachments(Object param) {
        List<Attachment> out = new ArrayList<>();
        if (param == null || (param instanceof String s && s.isBlank())) {
            return out;
        }
        List<?> items = param instanceof List<?> l ? l : List.of(param);
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m) || m.get("data") == null) {
                throw new MiniFlowException("error.email.attachment");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(String.valueOf(m.get("data")));
            } catch (IllegalArgumentException e) {
                throw new MiniFlowException("error.email.attachment");
            }
            String name = m.get("name") == null ? "attachment" : String.valueOf(m.get("name"));
            String mime = m.get("mimeType") == null ? "application/octet-stream" : String.valueOf(m.get("mimeType"));
            out.add(new Attachment(name, mime, bytes));
        }
        return out;
    }

    private static JavaMailSenderImpl sender(Map<String, String> c) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.get("host"));
        int port;
        try {
            port = Integer.parseInt(c.getOrDefault("port", "587").trim());
        } catch (NumberFormatException e) {
            throw new MiniFlowException("error.param.number", "port", c.get("port"));
        }
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        String username = c.get("username");
        Properties props = sender.getJavaMailProperties();
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(c.get("password"));
            props.put("mail.smtp.auth", "true");
        }
        props.put("mail.smtp.starttls.enable", c.getOrDefault("starttls", "true"));
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    private static String[] split(String addresses) {
        return Arrays.stream(addresses.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
