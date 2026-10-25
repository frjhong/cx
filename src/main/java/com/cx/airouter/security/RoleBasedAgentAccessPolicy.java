package com.cx.airouter.security;

import com.cx.airouter.agent.AgentDefinition;
import org.springframework.stereotype.Component;

/** 依 {@code ai-router.agents[].required-roles} 檢查呼叫者角色（任一符合即可）。 */
@Component
public class RoleBasedAgentAccessPolicy implements AgentAccessPolicy {

    @Override
    public boolean canInvoke(CallerContext caller, AgentDefinition agent) {
        return caller.hasAnyRole(agent.requiredRoles());
    }
}
