package com.upc.wms.agent.supervision;

import java.util.Map;

public interface BusinessInvariant {
    String name();

    InvariantResult verify(Map<String, Object> state);
}
