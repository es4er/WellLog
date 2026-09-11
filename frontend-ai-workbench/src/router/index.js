import { createRouter, createWebHistory } from 'vue-router'
import { useSession } from '../composables/useSession'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../pages/LoginPage.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      component: () => import('../layouts/HomeLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/workbench',
      children: [
        {
          path: 'workbench',
          name: 'workbench',
          component: () => import('../pages/WorkbenchPage.vue')
        },
        {
          path: 'agent-union',
          name: 'agent-union',
          component: () => import('../pages/AgentUnionPage.vue')
        },
        {
          path: 'plan-center',
          name: 'plan-center',
          component: () => import('../pages/PlanCenterPage.vue')
        },
        {
          path: 'outbound',
          name: 'outbound',
          component: () => import('../pages/OutboundBoardPage.vue')
        },
        {
          path: 'warehouse-outbound',
          name: 'warehouse-outbound',
          component: () => import('../pages/WarehouseOutboundPage.vue')
        },
        {
          path: 'outbound-orders',
          name: 'outbound-orders',
          redirect: { path: '/warehouse-outbound', query: { tab: 'pendingOrder' } }
        },
        {
          path: 'outbound-orders/:id',
          name: 'outbound-order-detail',
          component: () => import('../pages/OutboundOrderDetailPage.vue')
        },
        {
          path: 'warehouse-receiving',
          name: 'warehouse-receiving',
          component: () => import('../pages/WarehouseReceivingPage.vue')
        },
        {
          path: 'warehouse-picking',
          name: 'warehouse-picking',
          redirect: { path: '/warehouse-outbound', query: { tab: 'pendingPick' } }
        },
        {
          path: 'warehouse-picking/:id',
          name: 'warehouse-picking-detail',
          component: () => import('../pages/WarehousePickingDetailPage.vue')
        },
        {
          path: 'warehouse-pick-scan',
          name: 'warehouse-pick-scan',
          component: () => import('../pages/WarehousePickScanPage.vue')
        },
        {
          path: 'warehouse-pick-scan/:id',
          name: 'warehouse-pick-scan-task',
          component: () => import('../pages/WarehousePickScanPage.vue')
        },
        {
          path: 'warehouse-exceptions',
          name: 'warehouse-exceptions',
          component: () => import('../pages/WarehouseExceptionPage.vue')
        },
        {
          path: 'warehouse-exceptions/:id',
          name: 'warehouse-exception-detail',
          component: () => import('../pages/WarehouseExceptionDetailPage.vue')
        },
        {
          path: 'inventory-control',
          name: 'inventory-control',
          component: () => import('../pages/InventoryControlPage.vue')
        },
        {
          path: 'inventory-replenishment',
          name: 'inventory-replenishment',
          component: () => import('../pages/InventoryReplenishmentPage.vue')
        },
        {
          path: 'inventory-safety',
          name: 'inventory-safety',
          component: () => import('../pages/SafetyStockPage.vue')
        },
        {
          path: 'inventory-variance',
          name: 'inventory-variance',
          component: () => import('../pages/VarianceAnalysisPage.vue')
        },
        {
          path: 'inventory-frozen',
          name: 'inventory-frozen',
          component: () => import('../pages/FrozenCheckPage.vue')
        },
        {
          path: 'inventory-trace',
          name: 'inventory-trace',
          component: () => import('../pages/BatchTracePage.vue')
        },
        {
          path: 'inventory-check',
          name: 'inventory-check',
          component: () => import('../pages/InventoryCheckPage.vue')
        },
        {
          path: 'inventory-adjustment',
          name: 'inventory-adjustment',
          component: () => import('../pages/AdjustmentPage.vue')
        },
        {
          path: 'inventory-locations',
          name: 'inventory-locations',
          component: () => import('../pages/LocationMapPage.vue')
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('../pages/AnalyticsPage.vue')
        },
        {
          path: 'worker-today',
          name: 'worker-today',
          component: () => import('../pages/WorkerTodayPage.vue')
        },
        {
          path: 'worker-process',
          name: 'worker-process',
          component: () => import('../pages/WorkerProcessPage.vue')
        },
        {
          path: 'my-picking',
          name: 'my-picking',
          component: () => import('../pages/MyPickingPage.vue')
        },
        {
          path: 'scan-confirm',
          name: 'scan-confirm',
          component: () => import('../pages/ScanConfirmPage.vue')
        },
        {
          path: 'replenishment',
          name: 'replenishment',
          component: () => import('../pages/ReplenishmentPage.vue')
        },
        {
          path: 'worker-completed',
          name: 'worker-completed',
          component: () => import('../pages/WorkerCompletedPage.vue')
        },
        // ── 质检员模块（新增，与初版其他路由分离）──
        {
          path: 'quality/tasks',
          name: 'quality-tasks',
          component: () => import('../pages/quality/PendingTaskPage.vue')
        },
        {
          path: 'quality/execute',
          name: 'quality-execute',
          component: () => import('../pages/quality/InspectionExecutionPage.vue')
        },
        {
          path: 'quality/issues',
          name: 'quality-issues',
          component: () => import('../pages/quality/IssueCenterPage.vue')
        },
        {
          path: 'quality/standards',
          name: 'quality-standards',
          component: () => import('../pages/quality/InspectStandardPage.vue')
        },
        // ── 系统管理员模块 ──
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('../pages/admin/UserManagePage.vue')
        },
        {
          path: 'admin/roles',
          name: 'admin-roles',
          component: () => import('../pages/admin/RoleManagePage.vue')
        },
        {
          path: 'admin/permission-audit',
          name: 'admin-permission-audit',
          component: () => import('../pages/admin/PermissionAuditPage.vue')
        },
        {
          path: 'admin/login-audit',
          name: 'admin-login-audit',
          component: () => import('../pages/admin/LoginAuditPage.vue')
        },
        {
          path: 'admin/api-monitor',
          name: 'admin-api-monitor',
          component: () => import('../pages/admin/ApiMonitorPage.vue')
        },
        {
          path: 'admin/agent-monitor',
          name: 'admin-agent-monitor',
          component: () => import('../pages/admin/AgentMonitorPage.vue')
        },
        {
          path: 'admin/messages',
          name: 'admin-messages',
          component: () => import('../pages/admin/MessageCenterPage.vue')
        },
        {
          path: 'admin/evidence',
          name: 'admin-evidence',
          component: () => import('../pages/admin/EvidenceTracePage.vue')
        },
        {
          path: 'admin/config',
          name: 'admin-config',
          component: () => import('../pages/admin/SystemConfigPage.vue')
        },
        {
          path: 'admin/logs',
          name: 'admin-logs',
          component: () => import('../pages/admin/LogCenterPage.vue')
        },
        {
          path: 'admin/backup',
          name: 'admin-backup',
          component: () => import('../pages/admin/SystemBackupPage.vue')
        },
        {
          path: 'module/:menu',
          name: 'module',
          component: () => import('../pages/ModulePlaceholderPage.vue')
        }
      ]
    },
    {
      path: '/execution',
      name: 'execution',
      component: () => import('../pages/ExecutionPage.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/execution/report',
      name: 'agent-report',
      component: () => import('../pages/AgentReportPage.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/workbench'
    }
  ]
})

router.beforeEach((to) => {
  const { authed } = useSession()
  if (to.meta.requiresAuth && !authed.value) {
    return { name: 'login' }
  }
  if (to.meta.guest && authed.value) {
    return { name: 'workbench' }
  }
  return true
})

export default router
