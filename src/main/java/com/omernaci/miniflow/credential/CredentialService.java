package com.omernaci.miniflow.credential;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialType.Field;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CredentialService {

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() { };

    private final CredentialRepository repository;
    private final CredentialCrypto crypto;
    private final ObjectMapper mapper;

    public CredentialService(CredentialRepository repository, CredentialCrypto crypto, ObjectMapper mapper) {
        this.repository = repository;
        this.crypto = crypto;
        this.mapper = mapper;
    }

    public record Summary(String name, String type, LocalDateTime updatedAt) {
    }

    @Transactional(readOnly = true)
    public List<Summary> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(e -> new Summary(e.getName(), e.getType(), e.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Summary> summary(String name) {
        return repository.findByName(name).map(e -> new Summary(e.getName(), e.getType(), e.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public Map<String, String> require(String name, String... allowedTypes) {
        if (name == null || name.isBlank() || "null".equals(name)) {
            throw new MiniFlowException("error.credential.required", String.join(", ", allowedTypes));
        }
        CredentialEntity e = repository.findByName(name.trim())
                .orElseThrow(() -> new MiniFlowException("error.credential.notFound", name));
        if (allowedTypes.length > 0 && Arrays.stream(allowedTypes).noneMatch(e.getType()::equals)) {
            throw new MiniFlowException("error.credential.wrongType", name, e.getType(), String.join(", ", allowedTypes));
        }
        return decode(e);
    }

    @Transactional(readOnly = true)
    public Map<String, String> publicValues(String name) {
        CredentialEntity e = repository.findByName(name)
                .orElseThrow(() -> new MiniFlowException("error.credential.notFound", name));
        CredentialType type = type(e.getType());
        Map<String, String> data = tryDecode(e).orElse(Map.of());
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : type.fields()) {
            out.put(f.name(), f.secret() ? "" : data.getOrDefault(f.name(), ""));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> secretsPresent(String name) {
        CredentialEntity e = repository.findByName(name)
                .orElseThrow(() -> new MiniFlowException("error.credential.notFound", name));
        Map<String, String> data = tryDecode(e).orElse(Map.of());
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Field f : type(e.getType()).fields()) {
            if (f.secret()) {
                out.put(f.name(), data.get(f.name()) != null && !data.get(f.name()).isBlank());
            }
        }
        return out;
    }

    @Transactional
    public void save(String originalName, String name, String typeId, Map<String, String> values) {
        String cleanName = name == null ? "" : name.trim();
        if (!cleanName.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new MiniFlowException("error.credential.nameInvalid");
        }
        CredentialType type = type(typeId);

        CredentialEntity entity;
        Map<String, String> existing = Map.of();
        if (originalName == null || originalName.isBlank()) {
            if (repository.existsByName(cleanName)) {
                throw new MiniFlowException("error.credential.exists", cleanName);
            }
            entity = new CredentialEntity(cleanName, type.id());
        } else {
            entity = repository.findByName(originalName)
                    .orElseThrow(() -> new MiniFlowException("error.credential.notFound", originalName));
            if (!originalName.equals(cleanName) && repository.existsByName(cleanName)) {
                throw new MiniFlowException("error.credential.exists", cleanName);
            }
            if (entity.getType().equals(type.id())) {
                existing = tryDecode(entity).orElse(Map.of());
            }
        }

        Map<String, String> data = new LinkedHashMap<>();
        for (Field f : type.fields()) {
            String v = values.get(f.name());
            v = v == null ? "" : v.trim();
            if (v.isEmpty() && f.secret()) {
                v = existing.getOrDefault(f.name(), "");
            }
            if (!f.options().isEmpty() && !v.isEmpty() && !f.options().contains(v)) {
                throw new MiniFlowException("error.credential.fieldInvalid", f.name(), String.join(", ", f.options()));
            }
            if (v.isEmpty() && f.required()) {
                throw new MiniFlowException("error.credential.fieldRequired", f.name());
            }
            if (!v.isEmpty()) {
                data.put(f.name(), v);
            }
        }

        entity.setName(cleanName);
        entity.setType(type.id());
        entity.setDataEncrypted(crypto.encrypt(toJson(data)));
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Transactional
    public boolean delete(String name) {
        Optional<CredentialEntity> e = repository.findByName(name);
        e.ifPresent(repository::delete);
        return e.isPresent();
    }

    public static CredentialType type(String typeId) {
        return CredentialType.byId(typeId).orElseThrow(() -> new MiniFlowException("error.credential.typeInvalid", typeId));
    }

    @Transactional(readOnly = true)
    public boolean isReadable(String name) {
        return repository.findByName(name).map(e -> tryDecode(e).isPresent()).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> unreadable() {
        return repository.findAllByOrderByNameAsc().stream()
                .filter(e -> tryDecode(e).isEmpty())
                .map(CredentialEntity::getName)
                .toList();
    }

    private Optional<Map<String, String>> tryDecode(CredentialEntity e) {
        try {
            return Optional.of(decode(e));
        } catch (MiniFlowException ex) {
            return Optional.empty();
        }
    }

    private Map<String, String> decode(CredentialEntity e) {
        try {
            return mapper.readValue(crypto.decrypt(e.getDataEncrypted()), MAP);
        } catch (JacksonException ex) {
            throw new MiniFlowException("error.credential.decrypt");
        }
    }

    private String toJson(Map<String, String> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JacksonException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
