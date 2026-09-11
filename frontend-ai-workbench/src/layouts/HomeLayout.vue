<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useDisplayName } from '../composables/useRoleAccess'
import { useWorkspaceTabs } from '../composables/useWorkspaceTabs'
import { menuToPath, pathToMenu, isUnionRoute, resolveAdminDemoContext, demoNavigateTarget } from '../router/menuRoutes'
import { qualityNavGroups, qualityTopItems, qualityGuideLinks } from '../data/qualityNav'
import { pmcNavGroups, pmcTopItems, pmcGuideLinks } from '../data/pmcNav'
import { warehouseNavGroups, warehouseTopItems, warehouseGuideLinks } from '../data/warehouseNav'
import { workerNavGroups, workerTopItems, workerGuideLinks } from '../data/workerNav'
import { adminNavGroups, adminTopItems, adminGuideLinks } from '../data/adminNav'
import { inventoryNavGroups, inventoryTopItems, inventoryGuideLinks } from '../data/inventoryNav'
import { adminDemoRoleGroups, adminDemoSectionTitle } from '../data/adminDemoNav'
import { roles } from '../data/roles'

const route = useRoute()
const router = useRouter()
const { activeRole, logout } = useSession()
const { displayName, displayInitial } = useDisplayName()
const { showTabs, openTabs, activePath, activateTab, closeTab } = useWorkspaceTabs(activeRole)

const isUnionPage = computed(() => isUnionRoute(route.path))
const isWarehouseReceivingPage = computed(() => route.path === '/warehouse-receiving')
const isQualityRole = computed(() => activeRole.value.id === 'quality')
const isPmcRole = computed(() => activeRole.value.id === 'pmc')
const isWarehouseRole = computed(() => activeRole.value.id === 'warehouse')
const isWorkerRole = computed(() => activeRole.value.id === 'worker')
const isAdminRole = computed(() => activeRole.value.id === 'admin')
const isInventoryRole = computed(() => activeRole.value.id === 'inventory')
/** PMC / 仓管 / 库存 / 生产工人 / 系统管理员复用质检侧栏视觉（折叠、图标分组），不改动质检分支本身 */
const useStyledSidebar = computed(
  () =>
    isQualityRole.value ||
    isPmcRole.value ||
    isWarehouseRole.value ||
    isInventoryRole.value ||
    isWorkerRole.value ||
    isAdminRole.value
)
const currentMenu = computed(() => pathToMenu(route.path, activeRole.value.id))
const adminDemoContext = computed(() => {
  if (!isAdminRole.value) return null
  return resolveAdminDemoContext(route.path, route.query.demoRole)
})
const adminDemoRoleLabel = computed(() => {
  if (!adminDemoContext.value) return ''
  return roles.find((role) => role.id === adminDemoContext.value.roleId)?.label || ''
})
const sidebarCollapsed = ref(false)
/** 双层伸缩栏：一级 / 二级默认全部收起 */
const adminMgmtExpanded = ref(false)
const demoCenterExpanded = ref(false)
const expandedAdminGroup = ref(null)
const expandedDemoRole = ref(null)
/** 质检 / PMC / 仓管 / 库存 / 工人：分组手风琴，同时只展开一个 */
const expandedNavGroup = ref(null)

const activeNavConfig = computed(() => {
  if (isQualityRole.value) {
    return { topItems: qualityTopItems, groups: qualityNavGroups, guideLinks: qualityGuideLinks }
  }
  return roleGroupedNav.value
})

const collapsedNavItems = computed(() => {
  if (!activeNavConfig.value) return []
  return activeNavConfig.value.groups.flatMap((group) => group.items)
})

const roleGroupedNav = computed(() => {
  if (isAdminRole.value) return null
  if (isPmcRole.value) {
    return { topItems: pmcTopItems, groups: pmcNavGroups, guideLinks: pmcGuideLinks }
  }
  if (isWarehouseRole.value) {
    return { topItems: warehouseTopItems, groups: warehouseNavGroups, guideLinks: warehouseGuideLinks }
  }
  if (isInventoryRole.value) {
    return { topItems: inventoryTopItems, groups: inventoryNavGroups, guideLinks: inventoryGuideLinks }
  }
  if (isWorkerRole.value) {
    return { topItems: workerTopItems, groups: workerNavGroups, guideLinks: workerGuideLinks }
  }
  return null
})

function openMenu(item) {
  router.push(menuToPath(item, activeRole.value.id))
}

function openAdminMenu(item) {
  router.push(menuToPath(item.key ?? item, 'admin'))
}

function openDemoMenu(item, roleId) {
  const menu = item.key ?? item
  router.push(demoNavigateTarget(menu, roleId))
}

function isAdminMenuActive(item) {
  const key = item.key ?? item
  if (key === '工作台') {
    return route.path === '/workbench' && !adminDemoContext.value
  }
  return !adminDemoContext.value && currentMenu.value === key
}

function isDemoMenuActive(item, roleId) {
  return (
    adminDemoContext.value?.roleId === roleId &&
    adminDemoContext.value?.menuKey === (item.key ?? item)
  )
}

function openGuide(link) {
  if (link.menu) {
    router.push({ path: menuToPath(link.menu, activeRole.value.id), hash: '#qa-flow' })
  }
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function toggleNavGroup(title) {
  expandedNavGroup.value = expandedNavGroup.value === title ? null : title
}

function isNavGroupExpanded(title) {
  return expandedNavGroup.value === title
}

function isNavGroupActive(group) {
  return group.items.some((item) => currentMenu.value === item.key)
}

function toggleAdminMgmt() {
  const next = !adminMgmtExpanded.value
  adminMgmtExpanded.value = next
  if (next) demoCenterExpanded.value = false
}

function toggleDemoCenter() {
  const next = !demoCenterExpanded.value
  demoCenterExpanded.value = next
  if (next) adminMgmtExpanded.value = false
}

function toggleAdminGroup(title) {
  expandedAdminGroup.value = expandedAdminGroup.value === title ? null : title
}

function toggleDemoRole(roleId) {
  expandedDemoRole.value = expandedDemoRole.value === roleId ? null : roleId
}

function isAdminGroupExpanded(title) {
  return expandedAdminGroup.value === title
}

function isDemoRoleExpanded(roleId) {
  return expandedDemoRole.value === roleId
}

function isDemoRoleActive(roleId) {
  return adminDemoContext.value?.roleId === roleId
}

watch(
  [currentMenu, activeRole],
  () => {
    if (isAdminRole.value) return
    const groups = activeNavConfig.value?.groups
    if (!groups) return
    const menu = currentMenu.value
    if (!menu) return
    for (const group of groups) {
      if (group.items.some((item) => item.key === menu)) {
        expandedNavGroup.value = group.title
        break
      }
    }
  },
  { immediate: true }
)

watch(
  adminDemoContext,
  (ctx) => {
    if (!ctx) return
    expandedDemoRole.value = ctx.roleId
    demoCenterExpanded.value = true
    adminMgmtExpanded.value = false
  },
  { immediate: true }
)

watch(
  [currentMenu, isAdminRole, adminDemoContext],
  () => {
    if (!isAdminRole.value || adminDemoContext.value) return
    const menu = currentMenu.value
    if (!menu) return
    for (const group of adminNavGroups) {
      if (group.items.some((item) => item.key === menu)) {
        expandedAdminGroup.value = group.title
        adminMgmtExpanded.value = true
        demoCenterExpanded.value = false
        break
      }
    }
  },
  { immediate: true }
)
</script>

<template>
  <div :class="['home-shell', { 'sidebar-collapsed': sidebarCollapsed && useStyledSidebar }]">
    <aside :class="['home-sidebar', { 'quality-sidebar': useStyledSidebar }]">
      <div class="brand">
        <span class="brand-mark">W</span>
        <strong v-show="!sidebarCollapsed || !useStyledSidebar">WellLog WMS</strong>
      </div>

      <!-- 质检 / PMC / 仓管 / 库存 / 生产工人：分组手风琴侧栏 -->
      <nav v-if="activeNavConfig" class="home-nav quality-nav role-nav-accordion">
        <div v-if="activeNavConfig.topItems.length" class="quality-nav-top">
          <button
            v-for="item in activeNavConfig.topItems"
            :key="item.key"
            type="button"
            :class="{ active: currentMenu === item.key }"
            :title="item.label"
            @click="openMenu(item.key)"
          >
            <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
            <span v-show="!sidebarCollapsed">{{ item.label }}</span>
          </button>
        </div>

        <template v-if="!sidebarCollapsed">
          <div
            v-for="group in activeNavConfig.groups"
            :key="group.title"
            class="admin-accordion-section role-nav-group"
          >
            <button
              type="button"
              class="admin-accordion-header"
              :class="{ expanded: isNavGroupExpanded(group.title), active: isNavGroupActive(group) }"
              :aria-expanded="isNavGroupExpanded(group.title)"
              @click="toggleNavGroup(group.title)"
            >
              <span>{{ group.title }}</span>
              <i
                class="qn-icon accordion-chevron"
                :data-icon="isNavGroupExpanded(group.title) ? 'collapse' : 'expand'"
                aria-hidden="true"
              ></i>
            </button>
            <div v-show="isNavGroupExpanded(group.title)" class="role-nav-group-body">
              <button
                v-for="item in group.items"
                :key="item.key"
                type="button"
                :class="{ active: currentMenu === item.key }"
                :title="item.label"
                @click="openMenu(item.key)"
              >
                <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <button
            v-for="item in collapsedNavItems"
            :key="item.key"
            type="button"
            :class="{ active: currentMenu === item.key }"
            :title="item.label"
            @click="openMenu(item.key)"
          >
            <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
          </button>
        </template>

        <div v-show="!sidebarCollapsed" class="quality-guide-card">
          <p class="quality-guide-label">操作指引</p>
          <button
            v-for="link in activeNavConfig.guideLinks"
            :key="link.key"
            type="button"
            class="quality-guide-link"
            @click="openGuide(link)"
          >
            <span>{{ link.label }}</span>
            <em>›</em>
          </button>
        </div>

        <button type="button" class="quality-collapse-btn" @click="toggleSidebar">
          <i class="qn-icon" :data-icon="sidebarCollapsed ? 'expand' : 'collapse'" aria-hidden="true"></i>
          <span v-show="!sidebarCollapsed">收起菜单</span>
        </button>
      </nav>

      <!-- 系统管理员：系统管理 + 全角色演示中心 -->
      <nav v-else-if="isAdminRole" class="home-nav quality-nav admin-nav">
        <div class="quality-nav-top">
          <button
            v-for="item in adminTopItems"
            :key="item.key"
            type="button"
            :class="{ active: isAdminMenuActive(item) }"
            :title="item.label"
            @click="openAdminMenu(item)"
          >
            <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
            <span v-show="!sidebarCollapsed">{{ item.label }}</span>
          </button>
        </div>

        <div class="admin-accordion-section">
          <button
            v-show="!sidebarCollapsed"
            type="button"
            class="admin-accordion-header"
            :class="{ expanded: adminMgmtExpanded }"
            :aria-expanded="adminMgmtExpanded"
            @click="toggleAdminMgmt"
          >
            <span>系统管理</span>
            <i class="qn-icon accordion-chevron" :data-icon="adminMgmtExpanded ? 'collapse' : 'expand'" aria-hidden="true"></i>
          </button>

          <div v-show="adminMgmtExpanded && !sidebarCollapsed" class="admin-accordion-body">
            <div v-for="group in adminNavGroups" :key="group.title" class="admin-accordion-item">
              <button
                type="button"
                class="admin-accordion-subheader"
                :class="{ expanded: isAdminGroupExpanded(group.title) }"
                :aria-expanded="isAdminGroupExpanded(group.title)"
                @click="toggleAdminGroup(group.title)"
              >
                <span>{{ group.title }}</span>
                <i
                  class="qn-icon accordion-chevron"
                  :data-icon="isAdminGroupExpanded(group.title) ? 'collapse' : 'expand'"
                  aria-hidden="true"
                ></i>
              </button>
              <div v-show="isAdminGroupExpanded(group.title)" class="admin-accordion-subbody">
                <button
                  v-for="item in group.items"
                  :key="item.key"
                  type="button"
                  :class="{ active: isAdminMenuActive(item) }"
                  :title="item.label"
                  @click="openAdminMenu(item)"
                >
                  <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
                  <span>{{ item.label }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-demo-divider" aria-hidden="true"></div>

        <div class="admin-accordion-section admin-demo-section">
          <button
            v-show="!sidebarCollapsed"
            type="button"
            class="admin-accordion-header admin-demo-heading"
            :class="{ expanded: demoCenterExpanded }"
            :aria-expanded="demoCenterExpanded"
            @click="toggleDemoCenter"
          >
            <span>{{ adminDemoSectionTitle }}</span>
            <i class="qn-icon accordion-chevron" :data-icon="demoCenterExpanded ? 'collapse' : 'expand'" aria-hidden="true"></i>
          </button>

          <div v-show="demoCenterExpanded && !sidebarCollapsed" class="admin-accordion-body">
            <div
              v-for="roleGroup in adminDemoRoleGroups"
              :key="roleGroup.roleId"
              class="admin-accordion-item admin-demo-role-group"
            >
              <button
                type="button"
                class="admin-accordion-subheader admin-demo-role-title"
                :class="{ expanded: isDemoRoleExpanded(roleGroup.roleId), active: isDemoRoleActive(roleGroup.roleId) }"
                :aria-expanded="isDemoRoleExpanded(roleGroup.roleId)"
                @click="toggleDemoRole(roleGroup.roleId)"
              >
                <span>{{ roleGroup.title }}</span>
                <i
                  class="qn-icon accordion-chevron"
                  :data-icon="isDemoRoleExpanded(roleGroup.roleId) ? 'collapse' : 'expand'"
                  aria-hidden="true"
                ></i>
              </button>
              <div v-show="isDemoRoleExpanded(roleGroup.roleId)" class="admin-accordion-subbody">
                <button
                  v-for="item in roleGroup.items"
                  :key="`${roleGroup.roleId}-${item.key}`"
                  type="button"
                  :class="{ active: isDemoMenuActive(item, roleGroup.roleId) }"
                  :title="`${roleGroup.title} · ${item.label}`"
                  @click="openDemoMenu(item, roleGroup.roleId)"
                >
                  <i class="qn-icon" :data-icon="item.icon" aria-hidden="true"></i>
                  <span>{{ item.label }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-show="!sidebarCollapsed" class="quality-guide-card">
          <p class="quality-guide-label">操作指引</p>
          <button
            v-for="link in adminGuideLinks"
            :key="link.key"
            type="button"
            class="quality-guide-link"
            @click="openGuide(link)"
          >
            <span>{{ link.label }}</span>
            <em>›</em>
          </button>
        </div>

        <button type="button" class="quality-collapse-btn" @click="toggleSidebar">
          <i class="qn-icon" :data-icon="sidebarCollapsed ? 'expand' : 'collapse'" aria-hidden="true"></i>
          <span v-show="!sidebarCollapsed">收起菜单</span>
        </button>
      </nav>

      <!-- 其他角色：原扁平侧栏 -->
      <nav v-else class="home-nav">
        <button
          v-for="item in activeRole.visible"
          :key="item"
          :class="{ active: currentMenu === item }"
          type="button"
          @click="openMenu(item)"
        >{{ item }}</button>
      </nav>

      <div class="user-card">
        <span>{{ displayInitial }}</span>
        <div v-show="!sidebarCollapsed || !useStyledSidebar">
          <strong>{{ displayName }}</strong>
          <small>{{ activeRole.label }}</small>
        </div>
        <button
          v-show="!sidebarCollapsed || !useStyledSidebar"
          class="logout-button"
          type="button"
          title="退出登录"
          @click="logout"
        >退出</button>
      </div>
    </aside>

    <main
      :class="[
        'home-main',
        {
          'home-main-union': isUnionPage,
          'home-main-tabs': showTabs,
          'home-main-receiving': isWarehouseReceivingPage
        }
      ]"
    >
      <div v-if="showTabs" class="workspace-tabs" role="tablist" aria-label="已打开页面">
        <button
          v-for="tab in openTabs"
          :key="tab.path"
          type="button"
          role="tab"
          :class="['workspace-tab', { active: activePath === tab.path }]"
          :aria-selected="activePath === tab.path"
          :title="tab.label"
          @click="activateTab(tab)"
        >
          <span class="workspace-tab-label">{{ tab.label }}</span>
          <span
            v-if="tab.closable"
            class="workspace-tab-close"
            title="关闭"
            @click="closeTab(tab, $event)"
          >×</span>
        </button>
      </div>
      <div v-else-if="!isUnionPage" class="home-identity">
        <span v-if="adminDemoContext" class="identity-demo">演示预览 · {{ adminDemoRoleLabel }}</span>
        <span v-else class="identity-role">{{ activeRole.label }}</span>
      </div>
      <div class="home-main-body">
        <RouterView v-slot="{ Component }">
          <KeepAlive include="WorkerProcessPage">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* 收货上架页只铺满侧边栏右侧的主内容区，不使用 100vw。 */
.home-main-receiving {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
  overflow-x: hidden;
}

.home-main-receiving .home-main-body {
  width: 100%;
  max-width: none;
  min-width: 0;
  margin-right: 0;
  margin-left: 0;
  padding-right: 0;
  padding-top: 0;
  padding-left: 0;
  box-sizing: border-box;
}
</style>
