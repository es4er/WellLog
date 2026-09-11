package com.upc.wms.agent.capability;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单个领域智能体的能力契约：业务能力、可访问数据范围、结构化返回格式。
 */
@Data
@Builder
public class AgentCapability {

    /** 智能体名称，与 {@link com.upc.wms.agent.core.AgentNames} 一致 */
    private String agentName;
    private String label;
    /** decision / strategy / execution / auxiliary */
    private String layer;
    private String layerLabel;
    /** 职责简述 */
    private String responsibility;
    /** 业务能力列表 */
    private List<String> capabilities;
    /** 可读取的上下文/业务数据键（白名单；* 表示可读全量上下文） */
    private List<String> dataScope;
    /** 主要访问的数据表或领域对象 */
    private List<String> dataTables;
    /** 结构化返回字段说明 */
    private List<String> returnFields;
    /** 支持的任务类型 */
    private List<String> supportedTaskTypes;
    /** 是否允许执行写库业务动作 */
    private boolean canMutate;
}
