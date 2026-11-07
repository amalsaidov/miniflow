package com.omernaci.miniflow.variable;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.config.MiniFlowProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class VariableService {

    private static final Logger log = LoggerFactory.getLogger(VariableService.class);
    private static final String NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,99}";

    private final VariableRepository repository;
    private final MiniFlowProperties props;
    private volatile Map<String, String> cache = Map.of();

    public VariableService(VariableRepository repository, MiniFlowProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public record Row(String name, String value, String description, String source, LocalDateTime updatedAt) {

        public boolean fromDb() {
            return "db".equals(source);
        }
    }

    @PostConstruct
    public void init() {
        if (repository.count() == 0 && !props.vars().isEmpty()) {
            props.vars().forEach((name, value) -> {
                if (name.matches(NAME_PATTERN)) {
                    VariableEntity e = new VariableEntity(name);
                    e.setValue(value);
                    e.setDescription("application.properties");
                    e.setUpdatedAt(LocalDateTime.now());
                    repository.save(e);
                }
            });
            log.info("Copied {} variable(s) from configuration into the variables table", props.vars().size());
        }
        reload();
    }

    public Map<String, String> all() {
        return cache;
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(cache.get(name));
    }

    @Transactional(readOnly = true)
    public List<Row> list() {
        List<Row> rows = new ArrayList<>();
        Map<String, Boolean> inDb = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (VariableEntity e : repository.findAllByOrderByNameAsc()) {
            rows.add(new Row(e.getName(), e.getValue(), e.getDescription(), "db", e.getUpdatedAt()));
            inDb.put(e.getName(), true);
        }
        props.vars().forEach((name, value) -> {
            if (!inDb.containsKey(name)) {
                rows.add(new Row(name, value, null, "config", null));
            }
        });
        rows.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional
    public void save(String originalName, String name, String value, String description) {
        String clean = name == null ? "" : name.trim();
        if (!clean.matches(NAME_PATTERN)) {
            throw new MiniFlowException("error.variable.nameInvalid");
        }
        VariableEntity entity;
        if (originalName == null || originalName.isBlank()) {
            if (repository.findByNameIgnoreCase(clean).isPresent()) {
                throw new MiniFlowException("error.variable.exists", clean);
            }
            entity = new VariableEntity(clean);
        } else {
            entity = repository.findByName(originalName)
                    .orElseThrow(() -> new MiniFlowException("error.variable.notFound", originalName));
            final Long currentId = entity.getId();
            repository.findByNameIgnoreCase(clean)
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(other -> {
                        throw new MiniFlowException("error.variable.exists", clean);
                    });
        }
        entity.setName(clean);
        entity.setValue(value == null ? "" : value);
        entity.setDescription(description == null || description.isBlank() ? null : description.trim());
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        reload();
    }

    @Transactional
    public boolean delete(String name) {
        Optional<VariableEntity> e = repository.findByName(name);
        e.ifPresent(repository::delete);
        reload();
        return e.isPresent();
    }

    private void reload() {
        Map<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        merged.putAll(props.vars());
        repository.findAll().forEach(e -> merged.put(e.getName(), e.getValue() == null ? "" : e.getValue()));
        cache = Collections.unmodifiableMap(merged);
    }
}
