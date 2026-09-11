package com.upc.wms.agent.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentStatus;
import com.upc.wms.agent.vo.AgentExecutionLogVO;
import com.upc.wms.agent.vo.AgentStatusVO;
import com.upc.wms.agent.vo.AgentTaskStepVO;
import com.upc.wms.agent.vo.AgentTaskVO;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.entity.AgentExecutionLog;
import com.upc.wms.entity.AgentStatusSnapshot;
import com.upc.wms.entity.AgentTask;
import com.upc.wms.entity.AgentTaskStep;
import com.upc.wms.mapper.AgentExecutionLogMapper;
import com.upc.wms.mapper.AgentStatusSnapshotMapper;
import com.upc.wms.mapper.AgentTaskMapper;
import com.upc.wms.mapper.AgentTaskStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能体任务日志服务：统一负责任务、步骤、执行日志与状态快照的落库与查询。
 * <p>
 * 该服务本身不使用外层事务，日志写入独立于业务事务，保证即使业务失败也能记录过程。
 */
@Service
@RequiredArgsConstructor
public class AgentTaskLogService {

    private final AgentTaskMapper agentTaskMapper;
    private final AgentTaskStepMapper agentTaskStepMapper;
    private final AgentExecutionLogMapper agentExecutionLogMapper;
    private final AgentStatusSnapshotMapper agentStatusSnapshotMapper;

    // ---------------------------------------------------------- 任务

    public AgentTask createTask(String taskType, String taskName, String businessNo, Long createdBy) {
        LocalDateTime now = LocalDateTime.now();
        AgentTask task = new AgentTask();
        task.setTaskNo(NoGenerator.next("AT"));
        task.setTaskType(taskType);
        task.setTaskName(taskName);
        task.setBusinessNo(businessNo);
        task.setStatus(AgentStatus.RUNNING.name());
        task.setCreatedBy(createdBy);
        task.setStartTime(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        agentTaskMapper.insert(task);
        return task;
    }

    public void updateCurrentAgent(Long taskId, String agentName) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setCurrentAgent(agentName);
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    public void updateBusinessNo(Long taskId, String businessNo) {
        if (businessNo == null) {
            return;
        }
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || task.getBusinessNo() != null) {
            return;
        }
        task.setBusinessNo(businessNo);
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    public void finishTask(Long taskId, String status, String errorMessage) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        task.setEndTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    // ---------------------------------------------------------- 步骤

    public AgentTaskStep startStep(Long taskId, int stepNo, String agentName, String stepName, Object inputData) {
        LocalDateTime now = LocalDateTime.now();
        AgentTaskStep step = new AgentTaskStep();
        step.setTaskId(taskId);
        step.setStepNo(stepNo);
        step.setAgentName(agentName);
        step.setStepName(stepName);
        step.setStatus(AgentStatus.RUNNING.name());
        step.setInputData(AgentDataUtils.toJson(inputData));
        step.setStartTime(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        agentTaskStepMapper.insert(step);
        return step;
    }

    public void finishStep(AgentTaskStep step, String status, Object outputData, String nextAgent, String errorMessage) {
        finishStep(step, status, outputData, nextAgent, errorMessage, null);
    }

    public void finishStep(AgentTaskStep step, String status, Object outputData, String nextAgent,
                           String errorMessage, String stepName) {
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(status);
        step.setOutputData(AgentDataUtils.toJson(outputData));
        step.setNextAgent(nextAgent);
        step.setErrorMessage(errorMessage);
        if (stepName != null && !stepName.isBlank()) {
            step.setStepName(stepName);
        }
        step.setEndTime(now);
        if (step.getStartTime() != null) {
            step.setDurationMs(java.time.Duration.between(step.getStartTime(), now).toMillis());
        }
        step.setUpdatedAt(now);
        agentTaskStepMapper.updateById(step);
    }

    // ---------------------------------------------------------- 执行日志

    public void log(Long taskId, Long stepId, String agentName, String logType, String content) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setTaskId(taskId);
        log.setStepId(stepId);
        log.setAgentName(agentName);
        log.setLogType(logType);
        log.setContent(content != null && content.length() > 1000 ? content.substring(0, 1000) : content);
        log.setCreatedAt(LocalDateTime.now());
        agentExecutionLogMapper.insert(log);
    }

    // ---------------------------------------------------------- 状态快照

    public void updateSnapshot(String agentName, String status, Long currentTaskId, Boolean success, Long durationMs) {
        LocalDateTime now = LocalDateTime.now();
        AgentStatusSnapshot snapshot = agentStatusSnapshotMapper.selectOne(
                new LambdaQueryWrapper<AgentStatusSnapshot>().eq(AgentStatusSnapshot::getAgentName, agentName));
        boolean isNew = snapshot == null;
        if (isNew) {
            snapshot = new AgentStatusSnapshot();
            snapshot.setAgentName(agentName);
            snapshot.setTotalTaskCount(0);
            snapshot.setSuccessCount(0);
            snapshot.setFailedCount(0);
            snapshot.setAvgDurationMs(0L);
        }
        snapshot.setAgentLabel(AgentNames.label(agentName));
        snapshot.setStatus(status);
        snapshot.setCurrentTaskId(currentTaskId);
        snapshot.setLastActiveTime(now);
        snapshot.setUpdatedAt(now);

        if (success != null) {
            int total = snapshot.getTotalTaskCount() == null ? 0 : snapshot.getTotalTaskCount();
            long avg = snapshot.getAvgDurationMs() == null ? 0L : snapshot.getAvgDurationMs();
            long dur = durationMs == null ? 0L : durationMs;
            snapshot.setAvgDurationMs((avg * total + dur) / (total + 1));
            snapshot.setTotalTaskCount(total + 1);
            if (success) {
                snapshot.setSuccessCount((snapshot.getSuccessCount() == null ? 0 : snapshot.getSuccessCount()) + 1);
            } else {
                snapshot.setFailedCount((snapshot.getFailedCount() == null ? 0 : snapshot.getFailedCount()) + 1);
            }
        }

        if (isNew) {
            agentStatusSnapshotMapper.insert(snapshot);
        } else {
            agentStatusSnapshotMapper.updateById(snapshot);
        }
    }

    // ---------------------------------------------------------- 查询(VO)

    public AgentTaskVO getTaskVO(Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("智能体任务不存在");
        }
        AgentTaskVO vo = new AgentTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskType(task.getTaskType());
        vo.setBusinessNo(task.getBusinessNo());
        vo.setTaskName(task.getTaskName());
        vo.setStatus(task.getStatus());
        vo.setCurrentAgent(task.getCurrentAgent());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setErrorMessage(task.getErrorMessage());
        return vo;
    }

    public List<AgentTaskStepVO> getStepVOs(Long taskId) {
        List<AgentTaskStep> steps = agentTaskStepMapper.selectList(
                new LambdaQueryWrapper<AgentTaskStep>()
                        .eq(AgentTaskStep::getTaskId, taskId)
                        .orderByAsc(AgentTaskStep::getStepNo));
        List<AgentTaskStepVO> list = new ArrayList<>();
        for (AgentTaskStep s : steps) {
            AgentTaskStepVO vo = new AgentTaskStepVO();
            vo.setStepId(s.getId());
            vo.setStepNo(s.getStepNo());
            vo.setAgentName(s.getAgentName());
            vo.setAgentLabel(AgentNames.label(s.getAgentName()));
            vo.setStepName(s.getStepName());
            vo.setStatus(s.getStatus());
            vo.setInputData(s.getInputData());
            vo.setOutputData(s.getOutputData());
            vo.setNextAgent(s.getNextAgent());
            vo.setStartTime(s.getStartTime());
            vo.setEndTime(s.getEndTime());
            vo.setDurationMs(s.getDurationMs());
            vo.setErrorMessage(s.getErrorMessage());
            list.add(vo);
        }
        return list;
    }

    public List<AgentExecutionLogVO> getLogVOs(Long taskId) {
        List<AgentExecutionLog> logs = agentExecutionLogMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionLog>()
                        .eq(AgentExecutionLog::getTaskId, taskId)
                        .orderByAsc(AgentExecutionLog::getId));
        List<AgentExecutionLogVO> list = new ArrayList<>();
        for (AgentExecutionLog l : logs) {
            AgentExecutionLogVO vo = new AgentExecutionLogVO();
            vo.setLogId(l.getId());
            vo.setAgentName(l.getAgentName());
            vo.setAgentLabel(AgentNames.label(l.getAgentName()));
            vo.setLogType(l.getLogType());
            vo.setContent(l.getContent());
            vo.setCreatedAt(l.getCreatedAt());
            list.add(vo);
        }
        return list;
    }

    public List<AgentStatusVO> getStatusVOs() {
        List<AgentStatusSnapshot> snapshots = agentStatusSnapshotMapper.selectList(null);
        List<AgentStatusVO> list = new ArrayList<>();
        for (AgentStatusSnapshot s : snapshots) {
            AgentStatusVO vo = new AgentStatusVO();
            vo.setAgentName(s.getAgentName());
            vo.setAgentLabel(s.getAgentLabel());
            vo.setStatus(s.getStatus());
            vo.setCurrentTaskId(s.getCurrentTaskId());
            vo.setTotalTaskCount(s.getTotalTaskCount());
            vo.setSuccessCount(s.getSuccessCount());
            vo.setFailedCount(s.getFailedCount());
            vo.setAvgDurationMs(s.getAvgDurationMs());
            vo.setLastActiveTime(s.getLastActiveTime());
            list.add(vo);
        }
        return list;
    }

    public List<AgentTask> listTasks() {
        return agentTaskMapper.selectList(
                new LambdaQueryWrapper<AgentTask>().orderByDesc(AgentTask::getId));
    }
}
