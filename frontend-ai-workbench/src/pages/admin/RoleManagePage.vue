<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost } from '../../api'
import { useSession } from '../../composables/useSession'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

/** 岗位职责说明：对齐仓管/PMC/质检等真实岗位 */
const ROLE_PROFILES = {
  ADMIN: {
    dept: '信息部',
    mission: '系统配置、账号权限与审计追溯',
    duties: ['管理用户账号与角色绑定', '查看权限与登录审计', '监控接口与 Agent 运行', '维护系统配置与备份'],
    dataScope: 'ALL'
  },
  PMC: {
    dept: '计划部',
    mission: '客户订单排产、齐套校验与领料协同',
    duties: ['审核待排产客户订单', '按合格库存做齐套校验', '分型缺料并给出采购/调拨建议', '生成领料单交仓管出库'],
    dataScope: 'DEPT'
  },
  WAREHOUSE: {
    dept: '仓储部',
    mission: '收货上架、出库拣货与异常闭环',
    duties: ['根据领料单生成出库单', '分配并监管拣货任务', '复核交接与库存校验', '处理库存不足等仓库异常'],
    dataScope: 'DEPT'
  },
  QUALITY: {
    dept: '质量部',
    mission: '来料/过程检验与质量异常闭环',
    duties: ['处理待检任务与检测执行', '录入检验结果并判定', '分析质量异常根因', '维护检验标准'],
    dataScope: 'DEPT'
  },
  INVENTORY: {
    dept: '仓储部',
    mission: '库存准确率、安全库存与盘点调整',
    duties: ['扫描低于安全库存的物料', '排查冻结库存与异常批次', '定位账实盘点差异', '生成调整单与批次追溯'],
    dataScope: 'DEPT'
  },
  WORKER: {
    dept: '生产部',
    mission: '生产领料执行、扫码确认与现场反馈',
    duties: ['查看今日生产与待领料任务', '扫码确认批次与数量', '缺料时提交补料申请', '现场异常一键反馈'],
    dataScope: 'SELF'
  }
}

/** 权限组颜色映射（7 个业务模块对应颜色） */
const GROUP_COLORS = {
  '系统管理': '#587766',
  'PMC 计划': '#5b8def',
  '仓库管理': '#3d9b6e',
  '质量管理': '#d96b5c',
  '库存控制': '#e0a045',
  '生产执行': '#7b6fd6',
  '数据分析': '#4fa3d1'
}

/** 角色卡左侧色带颜色（按角色编码映射） */
const ROLE_STRIP_COLORS = {
  'ADMIN': '#587766',
  'PMC': '#5b8def',
  'WAREHOUSE': '#3d9b6e',
  'QUALITY': '#d96b5c',
  'INVENTORY': '#e0a045',
  'WORKER': '#7b6fd6'
}

/** 权限组中文名 */
const GROUP_LABELS = {
  '系统管理': '系统管理权限',
  'PMC 计划': 'PMC 计划权限',
  '仓库管理': '仓库管理权限',
  '质量管理': '质量管理权限',
  '库存控制': '库存控制权限',
  '生产执行': '生产执行权限',
  '数据分析': '数据分析权限'
}

/** 资源类型中文 */
const RESOURCE_TYPE_LABELS = {
  API: '接口',
  MENU: '菜单',
  BUTTON: '按钮',
  DATA: '数据'
}

/** 数据权限范围 */
const DATA_SCOPE_LABELS = {
  ALL: '全部数据',
  DEPT: '本部门',
  SELF: '仅本人'
}

const DATA_SCOPE_STYLES = {
  ALL: 'ok',
  DEPT: 'info',
  SELF: 'warn'
}

const roles = ref([])
const permissions = ref([])
const rolePermissions = ref([])
const users = ref([])
const selectedRoleId = ref(null)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const expandedGroups = ref({})

// 权限编辑状态
const editPermissionIds = ref(new Set())
const saving = ref(false)
const saveMsg = ref('')
const saveMsgType = ref('') // 'ok' | 'error'

const enabledRoleCount = computed(() => roles.value.filter((r) => r.roleStatus === 'ENABLED' || !r.roleStatus).length)

const boundUserCount = computed(() =>
  users.value.filter((u) => Array.isArray(u.roleIds) && u.roleIds.length > 0).length
)

const filteredRoles = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const rows = roles.value.map((role) => enrichRole(role))
  if (!kw) return rows
  return rows.filter((role) =>
    [role.roleName, role.roleCode, role.profile?.dept, role.profile?.mission]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(kw)
  )
})

const selectedRole = computed(() => {
  if (!selectedRoleId.value) return null
  return filteredRoles.value.find((r) => String(r.roleId) === String(selectedRoleId.value))
    ?? enrichRole(roles.value.find((r) => String(r.roleId) === String(selectedRoleId.value)))
})

const selectedPermissions = computed(() => {
  if (!selectedRole.value) return []
  const ids = new Set(
    rolePermissions.value
      .filter((rp) => String(rp.roleId) === String(selectedRole.value.roleId))
      .map((rp) => String(rp.permissionId))
  )
  return permissions.value.filter((p) => ids.has(String(p.permissionId)))
})

/** 所有权限按 permissionGroup 分组（用于可编辑的复选框列表） */
const allGroupedPermissions = computed(() => {
  const perms = permissions.value
  const groups = {}
  for (const perm of perms) {
    const group = perm.permissionGroup || '其他'
    if (!groups[group]) groups[group] = []
    groups[group].push(perm)
  }
  const ordered = Object.keys(GROUP_COLORS)
  const result = []
  const seen = new Set()
  for (const g of ordered) {
    if (groups[g]) { result.push({ group: g, permissions: groups[g] }); seen.add(g) }
  }
  for (const g of Object.keys(groups).sort()) {
    if (!seen.has(g)) result.push({ group: g, permissions: groups[g] })
  }
  return result
})

const selectedUsers = computed(() => {
  if (!selectedRole.value) return []
  return users.value.filter((u) =>
    (u.roleIds ?? []).some((id) => String(id) === String(selectedRole.value.roleId))
  )
})

function enrichRole(role) {
  if (!role) return null
  const code = (role.roleCode || '').toUpperCase()
  const profile = ROLE_PROFILES[code] ?? {
    dept: '—',
    mission: '系统业务角色',
    duties: [],
    dataScope: 'ALL'
  }
  const permissionCount = rolePermissions.value.filter(
    (rp) => String(rp.roleId) === String(role.roleId)
  ).length
  const userCount = users.value.filter((u) =>
    (u.roleIds ?? []).some((id) => String(id) === String(role.roleId))
  ).length
  return { ...role, profile, permissionCount, userCount }
}

function permissionName(permission) {
  return permission.permissionName || permission.permissionCode
}

function resourceTypeText(type) {
  return RESOURCE_TYPE_LABELS[type] || type || '-'
}

function statusText(status) {
  if (status === 'ENABLED' || !status) return '启用'
  if (status === 'DISABLED') return '禁用'
  return status
}

function dataScopeText(scope) {
  return DATA_SCOPE_LABELS[scope] || scope || '全部数据'
}

function dataScopeStyle(scope) {
  return DATA_SCOPE_STYLES[scope] || 'ok'
}

function groupColor(group) {
  return GROUP_COLORS[group] || '#8a9891'
}

function selectRole(roleId) {
  selectedRoleId.value = roleId
}

function toggleGroup(group) {
  expandedGroups.value[group] = !expandedGroups.value[group]
}

function isGroupExpanded(group) {
  // default expanded
  return expandedGroups.value[group] !== false
}

function goUserManage() {
  router.push('/admin/users')
}

async function loadData() {
  if (!allowed.value) return
  loading.value = true
  error.value = ''
  try {
    const [roleRows, permissionRows, linkRows, userRows] = await Promise.all([
      apiGet('/user/roles'),
      apiGet('/user/permissions'),
      apiGet('/user/role-permissions'),
      apiGet('/user/list')
    ])
    roles.value = roleRows ?? []
    permissions.value = permissionRows ?? []
    rolePermissions.value = linkRows ?? []
    users.value = userRows ?? []
    if (!selectedRoleId.value && roles.value.length) {
      selectedRoleId.value = roles.value[0].roleId
    }
  } catch (err) {
    error.value = err?.message || '加载角色权限失败'
  } finally {
    loading.value = false
  }
}

// 当前选中角色的权限 ID 集合（来自后端真实数据）
const currentPermIds = computed(() => {
  return new Set(
    rolePermissions.value
      .filter((rp) => String(rp.roleId) === String(selectedRole.value?.roleId))
      .map((rp) => String(rp.permissionId))
  )
})

// 是否有未保存的修改
const dirty = computed(() => {
  const cur = currentPermIds.value
  const edit = editPermissionIds.value
  if (cur.size !== edit.size) return true
  for (const id of cur) {
    if (!edit.has(id)) return true
  }
  return false
})

// 当切换角色时，同步编辑状态
watch(selectedRole, () => {
  editPermissionIds.value = new Set(currentPermIds.value)
  saveMsg.value = ''
})

// 切换单个权限的勾选
function togglePermission(permId) {
  const next = new Set(editPermissionIds.value)
  if (next.has(String(permId))) {
    next.delete(String(permId))
  } else {
    next.add(String(permId))
  }
  editPermissionIds.value = next
}

// 全选/取消全选某个分组
function toggleGroupAll(groupPerms) {
  const next = new Set(editPermissionIds.value)
  const allSelected = groupPerms.every((p) => next.has(String(p.permissionId)))
  for (const p of groupPerms) {
    if (allSelected) {
      next.delete(String(p.permissionId))
    } else {
      next.add(String(p.permissionId))
    }
  }
  editPermissionIds.value = next
}

// 保存修改
async function savePermissions() {
  if (!selectedRole.value || !dirty.value) return
  saving.value = true
  saveMsg.value = ''
  try {
    const permIds = Array.from(editPermissionIds.value).map(Number)
    await apiPost(`/user/role-permissions/update?roleId=${encodeURIComponent(selectedRole.value.roleId)}`, permIds)
    saveMsg.value = '权限已保存'
    saveMsgType.value = 'ok'
    // 刷新数据后同步编辑状态
    await loadData()
    // loadData 完成后 rolePermissions 已更新，重新同步
    const ids = new Set(
      rolePermissions.value
        .filter((rp) => String(rp.roleId) === String(selectedRole.value?.roleId))
        .map((rp) => String(rp.permissionId))
    )
    editPermissionIds.value = ids
  } catch (err) {
    saveMsg.value = err?.message || '保存失败'
    saveMsgType.value = 'error'
  } finally {
    saving.value = false
  }
}

// 撤销修改
function resetEdit() {
  editPermissionIds.value = new Set(currentPermIds.value)
  saveMsg.value = ''
}

watch(filteredRoles, (rows) => {
  if (!rows.length) {
    selectedRoleId.value = null
    return
  }
  if (!rows.some((r) => String(r.roleId) === String(selectedRoleId.value))) {
    selectedRoleId.value = rows[0].roleId
  }
})

onMounted(() => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('角色管理'))
    return
  }
  loadData()
})
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>角色管理</h1>
      </div>
      <div class="admin-header-meta">
        <button type="button" class="admin-btn" :disabled="loading" @click="loadData">刷新</button>
        <button type="button" class="admin-btn primary" @click="goUserManage">去绑定用户</button>
      </div>
    </header>

    <div class="admin-stat-strip">
      <div class="admin-stat-item">
        <strong>{{ roles.length }}</strong>
        <small>业务角色</small>
      </div>
      <div class="admin-stat-item ok">
        <strong>{{ enabledRoleCount }}</strong>
        <small>启用中</small>
      </div>
      <div class="admin-stat-item info">
        <strong>{{ permissions.length }}</strong>
        <small>权限能力</small>
      </div>
      <div class="admin-stat-item">
        <strong>{{ boundUserCount }}</strong>
        <small>已绑账号</small>
      </div>
    </div>

    <p v-if="error" class="login-error">{{ error }}</p>

    <!-- 双栏布局：左侧角色卡片 + 右侧详情 -->
    <div class="admin-grid-2 role-layout">
      <!-- 左侧：岗位角色列表 -->
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>岗位角色</h2>
          <div class="admin-toolbar">
            <input
              v-model="keyword"
              class="admin-search"
              type="search"
              placeholder="搜索角色 / 部门 / 职责"
            />
          </div>
        </div>

        <div class="role-card-list">
          <button
            v-for="role in filteredRoles"
            :key="role.roleId"
            type="button"
            class="role-card"
            :class="{ active: String(selectedRoleId) === String(role.roleId) }"
            :style="{ borderLeftColor: ROLE_STRIP_COLORS[(role.roleCode || '').toUpperCase()] || '#8a9891' }"
            @click="selectRole(role.roleId)"
          >
            <div class="role-card-top">
              <strong>{{ role.roleName }}</strong>
              <span :class="['admin-status', role.roleStatus === 'DISABLED' ? 'info' : 'ok']">
                {{ statusText(role.roleStatus) }}
              </span>
            </div>
            <p>{{ role.profile.mission }}</p>
            <div class="role-card-meta">
              <span>{{ role.profile.dept }}</span>
              <span>{{ role.userCount }} 人</span>
              <span>{{ role.permissionCount }} 项</span>
              <span :class="['data-scope-chip', dataScopeStyle(role.profile.dataScope)]">
                {{ dataScopeText(role.profile.dataScope) }}
              </span>
            </div>
          </button>
        </div>
        <p v-if="!loading && !filteredRoles.length" class="admin-empty">暂无匹配角色。</p>
      </div>

      <!-- 右侧：角色详情 -->
      <div class="admin-panel role-detail-panel">
        <template v-if="selectedRole">
          <div class="admin-panel-head">
            <div>
              <h2>{{ selectedRole.roleName }}</h2>
              <small>编码 {{ selectedRole.roleCode }} · {{ selectedRole.profile.dept }}</small>
            </div>
            <span :class="['admin-status', dataScopeStyle(selectedRole.profile.dataScope)]">
              {{ dataScopeText(selectedRole.profile.dataScope) }}
            </span>
          </div>

          <!-- 岗位职责 -->
          <div class="role-detail-mission">
            <strong>岗位职责</strong>
            <p>{{ selectedRole.profile.mission }}</p>
            <ul>
              <li v-for="duty in selectedRole.profile.duties" :key="duty">{{ duty }}</li>
            </ul>
          </div>

          <!-- 数据权限范围卡片 -->
          <div class="role-detail-block role-data-scope-block">
            <div class="admin-panel-head">
              <h2>数据权限</h2>
            </div>
            <div class="data-scope-detail">
              <div class="data-scope-icon" :class="dataScopeStyle(selectedRole.profile.dataScope)">
                <span v-if="selectedRole.profile.dataScope === 'ALL'">🌐</span>
                <span v-else-if="selectedRole.profile.dataScope === 'DEPT'">🏢</span>
                <span v-else>👤</span>
              </div>
              <div class="data-scope-info">
                <strong>{{ dataScopeText(selectedRole.profile.dataScope) }}</strong>
                <small v-if="selectedRole.profile.dataScope === 'ALL'">
                  可查看并操作系统中所有部门的数据，适合管理员与计划统筹岗位
                </small>
                <small v-else-if="selectedRole.profile.dataScope === 'DEPT'">
                  仅可访问所属部门范围内的数据，适合仓储、质量、库存等执行岗位
                </small>
                <small v-else>
                  仅可查看和操作本人的任务与记录，适合生产工人执行岗位
                </small>
              </div>
            </div>
          </div>

          <!-- 已授权限（可编辑） -->
          <div class="role-detail-block">
            <div class="admin-panel-head">
              <div>
                <h2>已授权限</h2>
                <small>{{ editPermissionIds.size }} / {{ permissions.length }} 项已选</small>
              </div>
              <div class="admin-toolbar">
                <button
                  type="button"
                  class="admin-btn"
                  :disabled="!dirty || saving"
                  @click="resetEdit"
                >撤销</button>
                <button
                  type="button"
                  class="admin-btn primary"
                  :disabled="!dirty || saving"
                  @click="savePermissions"
                >{{ saving ? '保存中...' : '保存授权' }}</button>
              </div>
            </div>
            <p v-if="saveMsg" :class="saveMsgType === 'error' ? 'login-error' : 'save-ok-msg'">
              {{ saveMsg }}
            </p>
            <template v-if="allGroupedPermissions.length">
              <div v-for="grp in allGroupedPermissions" :key="grp.group" class="role-perm-group">
                <button
                  type="button"
                  class="role-perm-group-title"
                  @click="toggleGroup(grp.group)"
                >
                  <span
                    class="role-perm-group-dot"
                    :style="{ background: groupColor(grp.group) }"
                  ></span>
                  <span class="role-perm-group-label">{{ GROUP_LABELS[grp.group] || grp.group }}</span>
                  <span class="role-perm-group-count">
                    {{ grp.permissions.filter(p => editPermissionIds.has(String(p.permissionId))).length }}/{{ grp.permissions.length }}
                  </span>
                  <span class="role-perm-group-arrow" :class="{ open: isGroupExpanded(grp.group) }">▾</span>
                </button>
                <div v-show="isGroupExpanded(grp.group)" class="role-perm-items">
                  <!-- 全选/取消全选 -->
                  <label class="role-perm-item role-perm-select-all">
                    <span class="role-perm-check">
                      <input
                        type="checkbox"
                        :checked="grp.permissions.every(p => editPermissionIds.has(String(p.permissionId)))"
                        :indeterminate="
                          grp.permissions.some(p => editPermissionIds.has(String(p.permissionId))) &&
                          !grp.permissions.every(p => editPermissionIds.has(String(p.permissionId)))
                        "
                        @change="toggleGroupAll(grp.permissions)"
                      />
                    </span>
                    <span class="role-perm-select-all-label">全选 / 取消</span>
                  </label>
                  <label
                    v-for="perm in grp.permissions"
                    :key="perm.permissionId"
                    class="role-perm-item role-perm-checkbox"
                    :class="{ checked: editPermissionIds.has(String(perm.permissionId)) }"
                  >
                    <span class="role-perm-check">
                      <input
                        type="checkbox"
                        :checked="editPermissionIds.has(String(perm.permissionId))"
                        @change="togglePermission(perm.permissionId)"
                      />
                    </span>
                    <div class="role-perm-body">
                      <strong>{{ permissionName(perm) }}</strong>
                      <small>{{ perm.permissionCode }}</small>
                    </div>
                    <div class="role-perm-tags">
                      <span class="perm-type-tag" :style="{ '--tag-color': groupColor(grp.group) }">
                        {{ resourceTypeText(perm.resourceType) }}
                      </span>
                      <span class="admin-meta-chip">{{ perm.resourcePath || '-' }}</span>
                    </div>
                  </label>
                </div>
              </div>
            </template>
            <p v-else class="admin-empty">系统中暂无权限定义。</p>
          </div>

          <!-- 在岗账号 -->
          <div class="role-detail-block">
            <div class="admin-panel-head">
              <h2>在岗账号</h2>
              <small>{{ selectedUsers.length }} 人</small>
            </div>
            <table v-if="selectedUsers.length" class="admin-table">
              <thead>
                <tr>
                  <th>账号</th>
                  <th>姓名</th>
                  <th>部门</th>
                  <th>状态</th>
                  <th>最近登录</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in selectedUsers" :key="user.userId">
                  <td>{{ user.userCode }}</td>
                  <td>{{ user.userName }}</td>
                  <td>{{ user.deptName || '-' }}</td>
                  <td>
                    <span :class="['admin-status', user.userStatus === 'ENABLED' ? 'ok' : 'info']">
                      {{ statusText(user.userStatus) }}
                    </span>
                  </td>
                  <td>{{ user.lastLoginAt || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="admin-empty">暂无账号绑定此角色，可前往用户管理分配。</p>
          </div>
        </template>
        <p v-else class="admin-empty">请选择左侧角色查看详情。</p>
      </div>
    </div>

  </section>
</template>
