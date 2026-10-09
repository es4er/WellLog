package com.upc.wms.agent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class WmsEvalCaseLoader {

    private final ObjectMapper objectMapper;

    public WmsEvalCaseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WmsEvalCase> load(InputStream input) throws IOException {
        return objectMapper.readValue(input, new TypeReference<>() { });
    }
}
