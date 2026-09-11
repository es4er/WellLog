package com.upc.wms.agent.core;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体上下文：在一次任务的多个智能体之间传递数据。
 * <p>
 * data 用于承载业务参数与各智能体产出的中间结果(如 receiptId、qualifiedLines 等)，
 * 智能体读取上一步写入的数据并追加自己的产出，实现链式协作。
 */
@Data
public class AgentContext {

    private Long taskId;
    private String taskNo;
    private String taskType;
    private String businessNo;
    private String currentAgent;
    private String sourceAgent;
    private Long createdBy;
    private Map<String, Object> data = new HashMap<>();

    public Object get(String key) {
        return data == null ? null : data.get(key);
    }

    public void put(String key, Object value) {
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key, value);
    }
}
