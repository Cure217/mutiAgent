package com.aliano.mutiagent.infrastructure.adapter;

import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.domain.instance.AppInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdapterRegistry {

    private final Map<String, AIAdapter> adapters = new HashMap<>();

    public AdapterRegistry(List<AIAdapter> adapterList) {
        for (AIAdapter adapter : adapterList) {
            adapters.put(adapter.getType(), adapter);
        }
    }

    public AIAdapter resolve(AppInstance instance) {
        if (StringUtils.hasText(instance.getAdapterType()) && adapters.containsKey(instance.getAdapterType())) {
            return adapters.get(instance.getAdapterType());
        }
        if ("codex".equalsIgnoreCase(instance.getAppType()) && adapters.containsKey("codex-cli")) {
            return adapters.get("codex-cli");
        }
        AIAdapter genericAdapter = adapters.get("generic-cli");
        if (genericAdapter == null) {
            throw new BusinessException("未找到可用的 AI 适配器");
        }
        return genericAdapter;
    }
}
