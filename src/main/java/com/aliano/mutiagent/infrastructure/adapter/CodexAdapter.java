package com.aliano.mutiagent.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CodexAdapter extends GenericCliAdapter {

    public CodexAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getType() {
        return "codex-cli";
    }
}
