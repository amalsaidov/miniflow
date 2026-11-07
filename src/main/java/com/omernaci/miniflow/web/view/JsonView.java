package com.omernaci.miniflow.web.view;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

@Component("json")
public class JsonView {

    private final ObjectWriter writer;

    public JsonView(ObjectMapper mapper) {
        this.writer = mapper.writerWithDefaultPrettyPrinter();
    }

    public String pretty(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
