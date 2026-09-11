package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户权限智能体：负责用户与权限相关操作。作为辅助智能体，不参与自动业务流程编排，
 * 可由其他智能体或接口按名称直接调用。
 */
@Component
@RequiredArgsConstructor
public class UserAgent implements Agent {

    private final UserService userService;

    @Override
    public String getName() {
        return AgentNames.USER;
    }

    @Override
    public boolean support(String taskType) {
        return false;
    }

    @Override
    public AgentResult handle(AgentContext context) {
        return AgentResult.success("用户权限智能体已就绪")
                .put("userCount", userService.listUsers().size());
    }
}
