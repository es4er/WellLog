package com.upc.wms.controller;

import com.upc.wms.agent.capability.AgentCapability;
import com.upc.wms.agent.core.WmsAgentOrchestrator;
import com.upc.wms.agent.messaging.AgentMessage;
import com.upc.wms.agent.platform.GraphTaskLaunch;
import com.upc.wms.agent.platform.WmsMultiAgentPlatform;
import com.upc.wms.agent.tool.ToolDescriptor;
import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.agent.service.AgentAiAnalysisService;
import com.upc.wms.agent.vo.AgentExecutionLogVO;
import com.upc.wms.agent.vo.AgentStatusVO;
import com.upc.wms.agent.vo.AgentTaskStepVO;
import com.upc.wms.agent.vo.AgentTaskVO;
import com.upc.wms.common.Result;
import com.upc.wms.dto.AgentAnalysisVO;
import com.upc.wms.dto.AgentTaskStartRequest;
import com.upc.wms.dto.PmcAssistantChatResponse;
import com.upc.wms.dto.WorkbenchAssistantChatRequest;
import com.upc.wms.entity.AgentTask;
import com.upc.wms.service.WorkbenchAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多智能体协作接口：供前端发起智能体任务并查询任务执行过程，用于协同监控台可视化。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final WmsAgentOrchestrator orchestrator;
    private final AgentAiAnalysisService agentAiAnalysisService;
    private final WorkbenchAssistantService workbenchAssistantService;
    private final WmsMultiAgentPlatform multiAgentPlatform;

    /**
     * 全角色工作台助手：先判意图再分流（问答 / 动作确认 / 澄清）。
     * 动作确认后由前端启动 Orchestrator（DeepSeek 规划 + 领域 Agent 真链）。
     */
    @PostMapping("/assistant/chat")
    public Result<PmcAssistantChatResponse> assistantChat(@RequestBody WorkbenchAssistantChatRequest request) {
        return Result.success(workbenchAssistantService.chat(request));
    }

    /**
     * 发起智能体任务。
     */
    @PostMapping("/task/start")
    public Result<Map<String, Object>> start(@RequestBody AgentTaskStartRequest request) {
        AgentTask task = orchestrator.startTaskAsync(request.getTaskType(), request.getTaskName(),
                request.getBusinessNo(), request.getData(), request.getCreatedBy());
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("taskNo", task.getTaskNo());
        data.put("status", task.getStatus());
        return Result.success("任务已启动", data);
    }

    /**
     * 使用 Workflow Graph + AgentMessage + Supervisor/Verifier 执行真实领域 Agent。
     */
    @PostMapping("/platform/task/run")
    public Result<Map<String, Object>> runGraphTask(@RequestBody AgentTaskStartRequest request) {
        GraphTaskLaunch launch = orchestrator.startGraphTask(request.getTaskType(), request.getTaskName(),
                request.getBusinessNo(), request.getData(), request.getCreatedBy());
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", launch.task().getId());
        data.put("taskNo", launch.task().getTaskNo());
        data.put("executionId", launch.execution().getExecutionId());
        data.put("status", launch.execution().getStatus());
        data.put("nodeStatuses", launch.execution().getNodeStatuses());
        return Result.success("Graph 多智能体任务执行完成", data);
    }

    @GetMapping("/platform/execution/{executionId}")
    public Result<WorkflowExecution> graphExecution(@PathVariable String executionId) {
        return Result.success(multiAgentPlatform.execution(executionId));
    }

    @GetMapping("/platform/execution/{executionId}/messages")
    public Result<List<AgentMessage>> graphMessages(@PathVariable String executionId) {
        return Result.success(multiAgentPlatform.trace(executionId));
    }

    @GetMapping("/platform/tools")
    public Result<List<ToolDescriptor>> platformTools() {
        return Result.success(multiAgentPlatform.tools());
    }

    /**
     * 查询任务详情。
     */
    @GetMapping("/task/{taskId}")
    public Result<AgentTaskVO> detail(@PathVariable Long taskId) {
        return Result.success(orchestrator.getTaskDetail(taskId));
    }

    /**
     * 查询任务步骤。
     */
    @GetMapping("/task/{taskId}/steps")
    public Result<List<AgentTaskStepVO>> steps(@PathVariable Long taskId) {
        return Result.success(orchestrator.getTaskSteps(taskId));
    }

    /**
     * 查询任务执行日志。
     */
    @GetMapping("/task/{taskId}/logs")
    public Result<List<AgentExecutionLogVO>> logs(@PathVariable Long taskId) {
        return Result.success(orchestrator.getTaskLogs(taskId));
    }

    /**
     * 查询任务 AI 分析报告（DeepSeek）。
     */
    @GetMapping("/task/{taskId}/analysis")
    public Result<AgentAnalysisVO> analysis(@PathVariable Long taskId) {
        return Result.success(agentAiAnalysisService.analyzeTask(taskId));
    }

    /**
     * 查询全部智能体能力契约（业务能力 / 数据范围 / 返回格式）。
     */
    @GetMapping("/capabilities")
    public Result<List<AgentCapability>> capabilities() {
        return Result.success(orchestrator.listCapabilities());
    }

    /**
     * 查询 Agent 状态列表。
     */
    @GetMapping("/status/list")
    public Result<List<AgentStatusVO>> statusList() {
        return Result.success(orchestrator.getStatusList());
    }

    /**
     * 查询任务列表。
     */
    @GetMapping("/task/list")
    public Result<List<AgentTask>> taskList() {
        return Result.success(orchestrator.listTasks());
    }
}
