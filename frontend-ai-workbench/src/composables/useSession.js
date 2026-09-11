import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { roles } from '../data/roles'
import { apiPost, clearAuthToken, setAuthToken } from '../api'
import { useWorker } from './useWorker'
import { clearAssistantState } from './assistantPanelState'

const SESSION_KEY = 'wms.auth.session'

const roleCodeMap = {
  ADMIN: 'admin',
  SYSTEM_ADMIN: 'admin',
  PMC: 'pmc',
  WAREHOUSE: 'warehouse',
  WH: 'warehouse',
  QUALITY: 'quality',
  QA: 'quality',
  INVENTORY: 'inventory',
  WORKER: 'worker'
}

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || '{}')
  } catch {
    return {}
  }
}

function saveSession(payload) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(payload))
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

function normalizeRoleId(roleId) {
  if (!roleId) return 'warehouse'
  const raw = String(roleId).trim()
  const upper = raw.toUpperCase()
  if (roleCodeMap[upper]) return roleCodeMap[upper]
  const lower = raw.toLowerCase()
  return roles.some((role) => role.id === lower) ? lower : 'warehouse'
}

function resolveRoleId(loginData) {
  const codes = loginData?.roleCodes ?? []
  const primary = loginData?.primaryRoleCode ?? codes[0]
  const fallbackCode = loginData?.user?.userCode
  const normalized = String(primary || fallbackCode || '').trim().toUpperCase()
  if (roleCodeMap[normalized]) {
    return roleCodeMap[normalized]
  }
  const lower = normalized.toLowerCase()
  return roles.some((role) => role.id === lower) ? lower : normalizeRoleId(fallbackCode)
}

const savedSession = loadSession()
const initialRoleId = normalizeRoleId(savedSession.roleId)

const authed = ref(Boolean(savedSession.token))
const activeRoleId = ref(initialRoleId)
const userId = ref(savedSession.userId ?? 1)
const currentUser = ref(savedSession.user ?? null)
const roleCodes = ref(savedSession.roleCodes ?? [])
const permissionCodes = ref(savedSession.permissionCodes ?? [])
const prompt = ref(roles.find((role) => role.id === activeRoleId.value)?.defaultPrompt ?? roles[0].defaultPrompt)
const activeStageIndex = ref(null)
const loginForm = reactive({ username: '', password: '' })
const loginError = ref('')

if (savedSession.token) {
  setAuthToken(savedSession.token)
  if (initialRoleId === 'worker' && savedSession.userId) {
    useWorker().setWorkerId(savedSession.userId)
  }
  if (savedSession.roleId && savedSession.roleId !== initialRoleId) {
    saveSession({ ...savedSession, roleId: initialRoleId })
  }
}

const activeRole = computed(
  () => roles.find((role) => role.id === activeRoleId.value) ?? roles.find((role) => role.id === 'warehouse')
)
const selectedStage = computed(() => activeStageIndex.value ?? activeRole.value.activeStage)

export function useSession() {
  const router = useRouter()

  async function login() {
    const userCode = loginForm.username.trim()
    if (!userCode || !loginForm.password) {
      loginError.value = '请输入账号和密码'
      return null
    }

    try {
      const data = await apiPost('/user/login', {
        userCode,
        password: loginForm.password
      })

      clearAssistantState()
      setAuthToken(data.token)

      const nextRoleId = normalizeRoleId(resolveRoleId(data))
      activeRoleId.value = nextRoleId
      userId.value = data.user?.userId ?? 1
      currentUser.value = data.user ?? null
      roleCodes.value = data.roleCodes ?? []
      permissionCodes.value = data.permissionCodes ?? []

      if (nextRoleId === 'worker') {
        useWorker().setWorkerId(userId.value)
      }

      authed.value = true
      activeStageIndex.value = null
      loginError.value = ''
      prompt.value = activeRole.value.defaultPrompt

      saveSession({
        token: data.token,
        roleId: nextRoleId,
        userId: userId.value,
        user: currentUser.value,
        roleCodes: roleCodes.value,
        permissionCodes: permissionCodes.value
      })

      return nextRoleId
    } catch (error) {
      clearAuthToken()
      clearSession()
      authed.value = false
      loginError.value = error?.message || '登录失败，请检查账号或密码'
      return null
    }
  }

  function logout() {
    clearAssistantState()
    clearAuthToken()
    clearSession()
    authed.value = false
    activeStageIndex.value = null
    currentUser.value = null
    roleCodes.value = []
    permissionCodes.value = []
    loginForm.username = ''
    loginForm.password = ''
    loginError.value = ''
    router.push('/login')
  }

  function useExample(text) {
    prompt.value = text
  }

  function goWorkbench(text) {
    if (text) prompt.value = text
    router.push('/workbench')
  }

  function fillDemo(username, password) {
    loginForm.username = username
    loginForm.password = password
  }

  return {
    authed,
    activeRoleId,
    userId,
    currentUser,
    roleCodes,
    permissionCodes,
    activeRole,
    prompt,
    activeStageIndex,
    selectedStage,
    loginForm,
    loginError,
    login,
    fillDemo,
    logout,
    useExample,
    goWorkbench
  }
}
