package com.upc.wms.agent.supervision;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WmsSafetyInvariant implements BusinessInvariant {

    private static final List<String> FORBIDDEN_TRUE_FLAGS = List.of(
            "negativeInventory",
            "frozenInventoryAllocated",
            "unqualifiedInventoryReleased",
            "unauthorizedAction",
            "duplicateInventoryDeduction"
    );

    @Override
    public String name() {
        return "wms-critical-safety";
    }

    @Override
    public InvariantResult verify(Map<String, Object> state) {
        for (String flag : FORBIDDEN_TRUE_FLAGS) {
            if (Boolean.TRUE.equals(state.get(flag))) {
                return InvariantResult.fail(name(), "Critical WMS safety flag is true: " + flag);
            }
        }
        return InvariantResult.pass(name());
    }
}
