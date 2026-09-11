<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost, apiPut } from '../../api'
import { useSession } from '../../composables/useSession'
import { downloadCsv } from '../../utils/tableExport'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

const users = ref([])
const roles = ref([])
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const msg = ref('')
const msgType = ref('')

// 预置部门选项
const PRESET_DEPTS = ['信息部', '计划部', '仓储部', '质量部', '生产部', '财务部', '人事部']

// 部门下拉选项（预置 + 已有用户部门去重）
const deptOptions = computed(() => {
  const existing = users.value.map((u) => u.deptName).filter(Boolean)
  const merged = new Set([...PRESET_DEPTS, ...existing])
  return Array.from(merged).sort()
})

// 部门选择：下拉值 或 "其他" 自定义
const deptSelect = ref('')
const deptCustom = ref('')

const actualDeptName = computed(() => {
  if (deptSelect.value === '__custom__') return deptCustom.value
  return deptSelect.value
})

// 分页
const page = ref(1)
const pageSize = 5

// 多选
const selectedIds = ref(new Set())

const form = reactive({
  userCode: '',
  userName: '',
  password: '',
  phone: '',
  email: '',
  deptName: '',
  roleId: ''
})

const editOpen = ref(false)
const editOriginalRoleId = ref('')
const editDeptSelect = ref('')
const editDeptCustom = ref('')
const editForm = reactive({
  userId: null,
  userCode: '',
  userName: '',
  userStatus: 'ENABLED',
  roleId: ''
})

const editActualDeptName = computed(() => {
  if (editDeptSelect.value === '__custom__') return editDeptCustom.value
  return editDeptSelect.value
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter((user) =>
    [user.userCode, user.userName, user.phone, user.email, user.deptName, user.userStatus]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(kw)
  )
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))

const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

const allChecked = computed(() => {
  if (!pagedUsers.value.length) return false
  return pagedUsers.value.every((u) => selectedIds.value.has(u.userId))
})

const checkedCount = computed(() => selectedIds.value.size)

function goPage(p) {
  if (p >= 1 && p <= totalPages.value) {
    page.value = p
  }
}

// 当搜索或数据变化时重置到第一页
function resetPage() {
  page.value = 1
}

function toggleSelectAll() {
  const next = new Set(selectedIds.value)
  if (allChecked.value) {
    for (const u of pagedUsers.value) next.delete(u.userId)
  } else {
    for (const u of pagedUsers.value) next.add(u.userId)
  }
  selectedIds.value = next
}

function toggleSelect(userId) {
  const next = new Set(selectedIds.value)
  if (next.has(userId)) {
    next.delete(userId)
  } else {
    next.add(userId)
  }
  selectedIds.value = next
}

async function batchDelete() {
  if (!selectedIds.value.size) return
  if (!confirm(`确认永久删除选中的 ${selectedIds.value.size} 个用户吗？此操作不可恢复。`)) return
  loading.value = true
  error.value = ''
  try {
    const ids = Array.from(selectedIds.value)
    await apiPost('/user/batch-delete', ids)
    msg.value = `已删除 ${ids.length} 个用户`
    msgType.value = 'ok'
    selectedIds.value = new Set()
    await loadData()
  } catch (err) {
    error.value = err?.message || '批量删除失败'
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  const data = filtered.value.map((u) => [
    u.userCode,
    u.userName,
    u.deptName || '',
    u.userStatus === 'ENABLED' ? '启用' : '禁用',
    u.lastLoginAt || '',
    (u.roleNames || []).join('; ')
  ])
  downloadCsv('用户列表.csv', ['账号', '姓名', '部门', '状态', '最近登录', '角色'], data)
}

async function loadData() {
  if (!allowed.value) return
  loading.value = true
  error.value = ''
  try {
    const [userRows, roleRows] = await Promise.all([
      apiGet('/user/list'),
      apiGet('/user/roles')
    ])
    users.value = userRows ?? []
    roles.value = roleRows ?? []
    resetPage()
  } catch (err) {
    error.value = err?.message || '加载用户失败'
  } finally {
    loading.value = false
  }
}

async function createUser() {
  if (!form.userCode || !form.userName) {
    error.value = '账号和姓名不能为空'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const password = encodeURIComponent(form.password || '123456')
    const created = await apiPost(`/user/add?password=${password}`, {
      userCode: form.userCode,
      userName: form.userName,
      phone: form.phone,
      email: form.email,
      deptName: actualDeptName.value,
      userStatus: 'ENABLED'
    })
    if (form.roleId) {
      await assignRole(created.userId, form.roleId)
    }
    Object.assign(form, {
      userCode: '',
      userName: '',
      password: '',
      phone: '',
      email: '',
      deptName: '',
      roleId: ''
    })
    deptSelect.value = ''
    deptCustom.value = ''
    await loadData()
  } catch (err) {
    error.value = err?.message || '创建用户失败'
  } finally {
    loading.value = false
  }
}

async function assignRole(userId, roleId) {
  if (!userId || !roleId) return
  await apiPost(`/user/assign-role?userId=${encodeURIComponent(userId)}&roleId=${encodeURIComponent(roleId)}`)
}

async function changeRole(user, event) {
  const nextRoleId = event.target.value
  if (!nextRoleId || String(currentRoleId(user)) === String(nextRoleId)) {
    return
  }
  const nextRoleName = roleName(nextRoleId)
  if (!confirm(`确认将「${user.userName}(${user.userCode})」的角色从「${roleName(currentRoleId(user))}」改为「${nextRoleName}」吗？`)) {
    event.target.value = currentRoleId(user)  // 恢复原值
    return
  }
  loading.value = true
  error.value = ''
  try {
    await assignRole(user.userId, nextRoleId)
    await loadData()
  } catch (err) {
    error.value = err?.message || '分配角色失败'
  } finally {
    loading.value = false
  }
}

async function disableUser(user) {
  loading.value = true
  error.value = ''
  try {
    await apiPut(`/user/${user.userId}/disable`)
    await loadData()
  } catch (err) {
    error.value = err?.message || '禁用用户失败'
  } finally {
    loading.value = false
  }
}

function openEditUser(user) {
  editForm.userId = user.userId
  editForm.userCode = user.userCode
  editForm.userName = user.userName || ''
  editForm.userStatus = user.userStatus || 'ENABLED'
  editForm.roleId = currentRoleId(user)
  editOriginalRoleId.value = currentRoleId(user)

  const dept = user.deptName || ''
  if (dept && deptOptions.value.includes(dept)) {
    editDeptSelect.value = dept
    editDeptCustom.value = ''
  } else if (dept) {
    editDeptSelect.value = '__custom__'
    editDeptCustom.value = dept
  } else {
    editDeptSelect.value = ''
    editDeptCustom.value = ''
  }

  error.value = ''
  msg.value = ''
  editOpen.value = true
}

function closeEditUser() {
  editOpen.value = false
}

async function saveEditUser() {
  if (!editForm.userName.trim()) {
    error.value = '姓名不能为空'
    return
  }
  loading.value = true
  error.value = ''
  msg.value = ''
  try {
    await apiPut('/user/update', {
      userId: editForm.userId,
      userCode: editForm.userCode,
      userName: editForm.userName.trim(),
      deptName: editActualDeptName.value,
      userStatus: editForm.userStatus
    })
    if (editForm.roleId && String(editForm.roleId) !== String(editOriginalRoleId.value)) {
      await assignRole(editForm.userId, editForm.roleId)
    }
    msg.value = `用户「${editForm.userName}」已更新`
    msgType.value = 'ok'
    editOpen.value = false
    await loadData()
  } catch (err) {
    error.value = err?.message || '更新用户失败'
  } finally {
    loading.value = false
  }
}

function roleName(roleId) {
  return roles.value.find((role) => String(role.roleId) === String(roleId))?.roleName ?? '-'
}

function currentRoleId(user) {
  return user?.roleIds?.[0] ?? ''
}

function statusText(status) {
  if (status === 'ENABLED') return '启用'
  if (status === 'DISABLED') return '禁用'
  return status || '-'
}

onMounted(() => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('用户管理'))
    return
  }
  loadData()
})
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>用户管理</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">{{ users.length }} 个用户</span>
        <span class="admin-meta-chip">{{ roles.length }} 个角色</span>
      </div>
    </header>

    <!-- 新增用户 -->
    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>新增用户</h2>
        <small>密码留空时默认使用 123456。</small>
      </div>
      <div class="admin-toolbar">
        <input v-model="form.userCode" class="admin-search" type="text" placeholder="账号" />
        <input v-model="form.userName" class="admin-search" type="text" placeholder="姓名" />
        <input v-model="form.password" class="admin-search" type="password" placeholder="密码" />
        <select v-model="deptSelect" class="admin-search">
          <option value="">选择部门</option>
          <option v-for="dept in deptOptions" :key="dept" :value="dept">{{ dept }}</option>
          <option value="__custom__">其他（手动输入）</option>
        </select>
        <input
          v-if="deptSelect === '__custom__'"
          v-model="deptCustom"
          class="admin-search"
          type="text"
          placeholder="输入部门名称"
        />
        <select v-model="form.roleId" class="admin-search">
          <option value="">暂不分配角色</option>
          <option v-for="role in roles" :key="role.roleId" :value="role.roleId">{{ role.roleName }}</option>
        </select>
        <button type="button" class="admin-btn primary" :disabled="loading" @click="createUser">创建</button>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>用户列表</h2>
        <div class="admin-toolbar">
          <input v-model="keyword" class="admin-search" type="search" placeholder="搜索账号 / 姓名 / 部门" @input="resetPage" />
          <button type="button" class="admin-btn" :disabled="loading" @click="loadData">刷新</button>
          <button
            type="button"
            class="admin-btn danger"
            :disabled="!checkedCount || loading"
            @click="batchDelete"
          >
            删除选中 {{ checkedCount ? `(${checkedCount})` : '' }}
          </button>
          <button type="button" class="admin-btn" @click="exportCsv">导出 CSV</button>
        </div>
      </div>

      <p v-if="error" class="login-error">{{ error }}</p>
      <p v-if="msg" :class="msgType === 'ok' ? 'save-ok-msg' : 'login-error'">{{ msg }}</p>

      <table class="admin-table">
        <thead>
          <tr>
            <th style="width:40px">
              <input type="checkbox" :checked="allChecked" @change="toggleSelectAll" />
            </th>
            <th>账号</th>
            <th>姓名</th>
            <th>部门</th>
            <th>状态</th>
            <th>最近登录</th>
            <th>角色</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in pagedUsers" :key="user.userId">
            <td>
              <input
                type="checkbox"
                :checked="selectedIds.has(user.userId)"
                @change="toggleSelect(user.userId)"
              />
            </td>
            <td>{{ user.userCode }}</td>
            <td>{{ user.userName }}</td>
            <td>{{ user.deptName || '-' }}</td>
            <td>
              <span :class="['admin-status', user.userStatus === 'ENABLED' ? 'ok' : 'info']">
                {{ statusText(user.userStatus) }}
              </span>
            </td>
            <td>{{ user.lastLoginAt || '-' }}</td>
            <td>
              <select class="admin-search" :value="currentRoleId(user)" @change="changeRole(user, $event)" style="min-width:120px">
                <option value="">分配角色</option>
                <option v-for="role in roles" :key="role.roleId" :value="role.roleId">
                  {{ roleName(role.roleId) }}
                </option>
              </select>
            </td>
            <td>
              <div class="user-row-actions">
                <button type="button" class="admin-btn" :disabled="loading" @click="openEditUser(user)">
                  编辑
                </button>
                <button
                  type="button"
                  class="admin-btn"
                  :disabled="loading || user.userStatus === 'DISABLED'"
                  @click="disableUser(user)"
                >
                  禁用
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="user-pagination" v-if="totalPages > 1">
        <button type="button" class="admin-btn" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <template v-for="p in totalPages" :key="p">
          <button
            type="button"
            class="admin-btn"
            :class="{ primary: p === page }"
            @click="goPage(p)"
          >{{ p }}</button>
        </template>
        <button type="button" class="admin-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
        <span class="page-info">共 {{ filtered.length }} 条，第 {{ page }}/{{ totalPages }} 页</span>
      </div>
      <p v-if="!loading && !filtered.length" class="admin-empty">暂无匹配用户。</p>
    </div>

    <div v-if="editOpen" class="admin-modal-overlay" @click.self="closeEditUser">
      <div class="admin-modal" role="dialog" aria-labelledby="edit-user-title">
        <header class="admin-modal-head">
          <div>
            <h3 id="edit-user-title">编辑用户</h3>
            <small>修改基本信息、部门、状态与角色绑定。</small>
          </div>
          <button type="button" class="admin-modal-close" aria-label="关闭" @click="closeEditUser">×</button>
        </header>
        <div class="admin-modal-body">
          <div class="admin-form-grid">
            <label class="admin-form-field">
              <span>账号</span>
              <input class="admin-search" type="text" :value="editForm.userCode" disabled />
            </label>
            <label class="admin-form-field">
              <span>姓名</span>
              <input v-model="editForm.userName" class="admin-search" type="text" placeholder="姓名" />
            </label>
            <label class="admin-form-field">
              <span>部门</span>
              <select v-model="editDeptSelect" class="admin-search">
                <option value="">未设置</option>
                <option v-for="dept in deptOptions" :key="dept" :value="dept">{{ dept }}</option>
                <option value="__custom__">其他（手动输入）</option>
              </select>
            </label>
            <label v-if="editDeptSelect === '__custom__'" class="admin-form-field">
              <span>自定义部门</span>
              <input v-model="editDeptCustom" class="admin-search" type="text" placeholder="输入部门名称" />
            </label>
            <label class="admin-form-field">
              <span>状态</span>
              <select v-model="editForm.userStatus" class="admin-search">
                <option value="ENABLED">启用</option>
                <option value="DISABLED">禁用</option>
              </select>
            </label>
            <label class="admin-form-field">
              <span>角色</span>
              <select v-model="editForm.roleId" class="admin-search">
                <option value="">暂不分配角色</option>
                <option v-for="role in roles" :key="role.roleId" :value="role.roleId">
                  {{ role.roleName }}
                </option>
              </select>
            </label>
          </div>
        </div>
        <footer class="admin-modal-foot">
          <button type="button" class="admin-btn" :disabled="loading" @click="closeEditUser">取消</button>
          <button type="button" class="admin-btn primary" :disabled="loading" @click="saveEditUser">保存</button>
        </footer>
      </div>
    </div>
  </section>
</template>
