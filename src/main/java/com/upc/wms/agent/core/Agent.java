package com.upc.wms.agent.core;

/**
 * 领域智能体统一接口。
 * <p>
 * 每个智能体只负责自己业务域内的编排，通过调用 Service 完成实际业务操作，
 * 不直接访问 Mapper 或数据库，从而保证事务一致性由 Service 层统一保证。
 */
public interface Agent {

    /**
     * 智能体名称(唯一)，用于调度时按名称查找，见 {@link AgentNames}。
     */
    String getName();

    /**
     * 是否支持处理该任务类型。
     */
    boolean support(String taskType);

    /**
     * 执行智能体逻辑并返回结果(含下一步智能体)。
     */
    AgentResult handle(AgentContext context);
}
