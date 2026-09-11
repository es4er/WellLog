package com.upc.wms.service.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchTopicAclTest {

    private WorkbenchTopicAcl acl;

    @BeforeEach
    void setUp() {
        acl = new WorkbenchTopicAcl();
    }

    @Test
    void pmc_roleAuthorizationQuestion_isDenied() {
        String deny = acl.denyReason("pmc", "查看当前系统的角色授权情况");
        assertNotNull(deny);
        assertTrue(deny.contains("无权限"));
        assertTrue(deny.contains("角色授权"));
    }

    @Test
    void warehouse_roleAuthorizationQuestion_isDenied() {
        String deny = acl.denyReason("warehouse", "查看当前系统的角色授权情况");
        assertNotNull(deny);
        assertTrue(deny.contains("无权限"));
        assertTrue(deny.contains("角色授权"));
    }

    @Test
    void admin_roleAuthorizationQuestion_isAllowed() {
        assertNull(acl.denyReason("admin", "查看当前系统的角色授权情况"));
    }

    @Test
    void pmc_kitQuery_isAllowed() {
        assertNull(acl.denyReason("pmc", "当前有哪些计划缺料？齐套情况怎么样？"));
    }
}
