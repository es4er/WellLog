import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api'
import {
  CATEGORY_OPTIONS,
  DEMO_WORKER_USER_ID,
  EXCEPTION_TYPES,
  REPLENISH_REASONS,
  TODAY,
  initialPickingTasks,
  initialScanRecords,
  initialWorkerExceptions
} from '../data/workerConstants'

const SESSION_KEY = 'wms.auth.session'

/** 演示工人赵工（sys_user.user_id=20）；管理员预览工人端时也使用该账号拉取任务 */
function resolveEffectiveWorkerId() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY) || '{}')
    if (session.roleId === 'worker' && session.userId) {
      return Number(session.userId) || DEMO_WORKER_USER_ID
    }
  } catch {
    /* ignore */
  }
  return DEMO_WORKER_USER_ID
}

const workerId = ref(resolveEffectiveWorkerId())
const pickingTasks = ref([])
const useLiveApi = ref(true)
const scanRecords = ref([])
const workerExceptions = ref([])
const replenishRecords = ref([])
const workerNotifications = ref([])
const completionRecords = ref([])
const transferRecords = ref([])
const activeTaskId = ref('')
const activeMaterialId = ref('')
const workerDataLoading = ref(false)
const workerDataError = ref('')
const lastScanFeedback = ref(null)
const lastSubmittedException = ref(null)

function refreshAllTaskStatuses() {
  pickingTasks.value.forEach((task) => {
    task.items.forEach(refreshItemStatus)
    refreshTaskStatus(task)
  })
}

function applyWorkbenchData(data) {
  if (!data) return
  pickingTasks.value = (data.tasks ?? []).map((task) => ({
    ...task,
    priority: task.priority || '中',
    unit: task.unit || '套',
    items: (task.items ?? []).map((item) => ({
      ...item,
      spec: item.spec || item.specification || '—',
      location: item.location || item.binCode || '—',
      available: item.available ?? item.availableQty ?? item.required,
      pickingLineId: item.pickingLineId ?? (Number(String(item.id).replace('item-', '')) || null)
    }))
  }))
  workerExceptions.value = (data.exceptions ?? []).map((ex) => ({
    ...ex,
    taskId: ex.taskId ?? (ex.pickingTaskId ? `pick-${ex.pickingTaskId}` : ''),
    itemId: ex.itemId ?? ex.pickingLineId ?? ''
  }))
  scanRecords.value = (data.scanRecords ?? []).map((record) => ({ ...record }))
  replenishRecords.value = (data.replenishRecords ?? []).map((row) => ({ ...row }))
  workerNotifications.value = (data.notifications ?? []).map((row) => ({ ...row }))
  completionRecords.value = (data.completions ?? []).map((row) => ({ ...row }))
  transferRecords.value = (data.transfers ?? []).map((row) => ({ ...row }))
  refreshAllTaskStatuses()
  if (pickingTasks.value.length && !pickingTasks.value.find((t) => t.id === activeTaskId.value)) {
    activeTaskId.value = pickingTasks.value[0].id
    activeMaterialId.value = pickingTasks.value[0].items?.[0]?.id || ''
  }
}

function setWorkerId(id) {
  workerId.value = id ?? DEMO_WORKER_USER_ID
}

function syncWorkerIdFromSession() {
  workerId.value = resolveEffectiveWorkerId()
}

function resolvePickingTaskId(task) {
  const fromField = task?.pickingTaskId
  if (fromField) return fromField
  const parsed = Number(String(task?.id ?? '').replace('pick-', ''))
  return Number.isFinite(parsed) ? parsed : null
}

function resolvePickingLineId(material) {
  const fromField = material?.pickingLineId
  if (fromField) return fromField
  const parsed = Number(String(material?.id ?? '').replace('item-', ''))
  return Number.isFinite(parsed) ? parsed : null
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function parseBarcode(value) {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parts = trimmed.split('-')
  if (parts.length >= 3) {
    return {
      batch: parts[0],
      materialCode: parts[1],
      serial: parts.slice(2).join('-'),
      raw: trimmed
    }
  }
  return { batch: '', materialCode: '', serial: trimmed, raw: trimmed }
}

function itemStatus(item) {
  if (item.scanned >= item.required) return '已备齐'
  if (item.scanned > 0 && item.scanned < item.required) return `缺 ${item.required - item.scanned}`
  return '待备料'
}

function refreshItemStatus(item) {
  item.status = item.status || itemStatus(item)
  item.shortage = Math.max(0, item.required - item.scanned)
}

function isTaskHandedOver(task) {
  return task.status === '已交接' || task.status === '已完成' || Boolean(task.handoverTime)
}

function isTaskPending(task) {
  if (task.cancelled) return false
  return !isTaskHandedOver(task)
}

function taskStatus(task) {
  if (task.cancelled) return '已取消'
  if (isTaskHandedOver(task)) return '已交接'
  if (task.status === '补料/异常' || task.status === '缺件待补拣') return '补料/异常'
  return '备料区待领'
}

function refreshTaskStatus(task) {
  /* 状态由后端 workbench 接口返回，前端不再根据扫码重算 */
}

function taskProgress(task) {
  const totalItems = task.items.length
  const preparedItems = task.items.filter((item) => item.scanned >= item.required).length
  const totalQty = task.items.reduce((sum, item) => sum + item.required, 0)
  const preparedQty = task.items.reduce((sum, item) => sum + Math.min(item.scanned, item.required), 0)
  return {
    totalItems,
    scannedItems: preparedItems,
    totalQty,
    scannedQty: preparedQty
  }
}

function overdueDays(planDate) {
  if (planDate >= TODAY) return 0
  const plan = new Date(`${planDate}T00:00:00`)
  const today = new Date(`${TODAY}T00:00:00`)
  return Math.floor((today - plan) / 86400000)
}

function isOverdueTask(task) {
  return task.planDate < TODAY && !isTaskHandedOver(task) && !task.cancelled
}

function matchScope(task, scope) {
  if (scope === 'today') return task.planDate === TODAY && !task.cancelled
  if (scope === 'overdue') return isOverdueTask(task)
  if (scope === 'all-pending') return isTaskPending(task)
  if (scope === 'history') {
    return isTaskHandedOver(task) || task.cancelled || workerExceptions.value.some(
      (ex) => ex.workOrder === task.workOrder && ex.status === '已关闭'
    )
  }
  if (scope === 'default') return (task.planDate === TODAY || isOverdueTask(task)) && !task.cancelled
  return true
}

function displayTaskStatus(task) {
  if (task.status === '备料区待领' || task.status === '备料区') return '备料区待领'
  if (task.status === '补料/异常' || task.status === '缺件待补拣') return '补料/异常'
  if (task.status === '已交接') return '已完成'
  return task.status || '备料区待领'
}

export function useWorker() {
  function tasksInScope(scope) {
    return pickingTasks.value.filter((task) => matchScope(task, scope))
  }

  function pendingScanItemsInScope(scope) {
    return tasksInScope(scope).flatMap((task) =>
      task.items
        .filter((item) => item.scanned < item.required)
        .map((item) => ({
          ...item,
          taskId: task.id,
          workOrder: task.workOrder,
          requisition: task.requisition,
          product: task.product,
          planDate: task.planDate,
          expectedTime: task.expectedTime,
          handler: task.handler,
          overdueDays: overdueDays(task.planDate),
          remaining: item.required - item.scanned
        }))
    )
  }

  function categoryCounts(scope) {
    const scoped = tasksInScope(scope)
    const pendingItems = pendingScanItemsInScope(scope)
    const handedOver = scoped.filter((task) => isTaskHandedOver(task))
    const openExceptions = workerExceptions.value.filter((ex) => {
      if (ex.status === '已关闭') return scope === 'history'
      const task = pickingTasks.value.find((t) => t.workOrder === ex.workOrder)
      if (!task) return scope === 'all-pending' || scope === 'default'
      return matchScope(task, scope)
    })
    return {
      overview: scoped.filter((task) => !task.cancelled).length,
      pendingScan: pendingItems.length,
      handedOver: handedOver.length,
      exceptions: openExceptions.filter((ex) => ex.status !== '已关闭').length
    }
  }

  const taskOverview = computed(() => {
    const scopeCounts = categoryCounts('default')
    return CATEGORY_OPTIONS.map((cat) => [cat.label, formatCount(cat.countKey, scopeCounts)])
  })

  function formatCount(key, counts) {
    if (key === 'pendingScan') return `${counts.pendingScan} 项`
    if (key === 'handedOver') return `${counts.handedOver} 项`
    if (key === 'exceptions') return `${counts.exceptions} 项`
    return String(counts.overview)
  }

  const activeTask = computed(
    () => pickingTasks.value.find((task) => task.id === activeTaskId.value) ?? pickingTasks.value[0] ?? null
  )

  const activeMaterial = computed(() => {
    const task = activeTask.value
    if (!task) return null
    const byId = task.items.find((item) => item.id === activeMaterialId.value)
    if (byId && byId.scanned < byId.required) return byId
    return task.items.find((item) => item.scanned < item.required) ?? task.items[0] ?? null
  })

  const currentScanProgress = computed(() => {
    const material = activeMaterial.value
    if (!material) return { required: 0, scanned: 0, remaining: 0 }
    return {
      required: material.required,
      scanned: material.scanned,
      remaining: Math.max(0, material.required - material.scanned)
    }
  })

  const taskScanRecords = computed(() => {
    const task = activeTask.value
    if (!task) return scanRecords.value
    return scanRecords.value.filter((record) => record.workOrder === task.workOrder)
  })

  function setActiveTask(taskId, materialId) {
    activeTaskId.value = taskId
    if (materialId) {
      activeMaterialId.value = materialId
    } else {
      const task = pickingTasks.value.find((t) => t.id === taskId)
      const next = task?.items.find((item) => item.scanned < item.required)
      activeMaterialId.value = next?.id ?? task?.items[0]?.id ?? ''
    }
  }

  async function loadWorkerData() {
    syncWorkerIdFromSession()
    workerDataLoading.value = true
    workerDataError.value = ''
    try {
      if (useLiveApi.value) {
        const data = await apiGet(`/worker/workbench?workerId=${workerId.value}`)
        applyWorkbenchData(data)
        return
      }
      pickingTasks.value = structuredClone(initialPickingTasks)
      scanRecords.value = structuredClone(initialScanRecords)
      workerExceptions.value = structuredClone(initialWorkerExceptions)
      refreshAllTaskStatuses()
    } catch (error) {
      workerDataError.value = error.message || '加载领料数据失败'
      // API 失败时回退本地演示数据，保证页面可演示
      if (!pickingTasks.value.length) {
        pickingTasks.value = structuredClone(initialPickingTasks)
        scanRecords.value = structuredClone(initialScanRecords)
        workerExceptions.value = structuredClone(initialWorkerExceptions)
      }
      refreshAllTaskStatuses()
    } finally {
      workerDataLoading.value = false
    }
  }

  async function loadReplenishRecords() {
    if (!useLiveApi.value) return replenishRecords.value
    try {
      const list = await apiGet(`/worker/replenish?workerId=${workerId.value}`)
      replenishRecords.value = Array.isArray(list) ? list : []
      return replenishRecords.value
    } catch (error) {
      throw error
    }
  }

  async function submitReplenish(payload) {
    if (useLiveApi.value) {
      const entry = await apiPost('/worker/replenish', {
        workerId: workerId.value,
        pickingTaskId: payload.pickingTaskId,
        pickingLineId: payload.pickingLineId,
        qty: payload.qty,
        reason: payload.reason,
        note: payload.note
      })
      replenishRecords.value = [entry, ...replenishRecords.value.filter((r) => r.id !== entry.id)]
      return entry
    }
    const entry = {
      id: `rep-${Date.now()}`,
      workOrder: payload.workOrder,
      product: payload.product,
      material: payload.material,
      materialCode: payload.materialCode,
      spec: payload.spec,
      qty: payload.qty,
      reason: payload.reason,
      note: payload.note,
      status: '待仓管复核',
      submittedAt: nowTime(),
      handler: '赵工'
    }
    replenishRecords.value.unshift(entry)
    return entry
  }

  async function markNotificationRead(notificationId) {
    if (!useLiveApi.value || !notificationId) return
    try {
      await apiPost(`/worker/notifications/${notificationId}/read?workerId=${workerId.value}`)
      const row = workerNotifications.value.find((n) => n.id === notificationId)
      if (row) row.read = true
    } catch {
      /* ignore */
    }
  }

  async function submitCompletion(payload) {
    if (useLiveApi.value) {
      const entry = await apiPost('/worker/completion', {
        workerId: workerId.value,
        workOrderNo: payload.workOrderNo,
        barcodeValue: payload.barcodeValue,
        qty: payload.qty,
        remark: payload.remark
      })
      completionRecords.value = [entry, ...completionRecords.value.filter((r) => r.id !== entry.id)]
      return entry
    }
    const entry = {
      id: `cmp-${Date.now()}`,
      workOrderNo: payload.workOrderNo,
      materialName: payload.materialName ?? '成品',
      materialCode: payload.materialCode ?? '',
      batchNo: payload.batchNo ?? '',
      qty: payload.qty ?? 1,
      locationCode: 'WIP-A01',
      status: '已入库',
      submittedAt: nowTime()
    }
    completionRecords.value.unshift(entry)
    return entry
  }

  async function submitTransfer(payload) {
    if (useLiveApi.value) {
      const entry = await apiPost('/worker/transfer', {
        workerId: workerId.value,
        transferCard: payload.transferCard,
        containerCode: payload.containerCode,
        fromProcess: payload.fromProcess,
        toProcess: payload.toProcess,
        workOrderNo: payload.workOrderNo,
        qty: payload.qty,
        remark: payload.remark
      })
      transferRecords.value = [entry, ...transferRecords.value.filter((r) => r.id !== entry.id)]
      return entry
    }
    const entry = {
      id: `trf-${Date.now()}`,
      transferCard: payload.transferCard,
      containerCode: payload.containerCode,
      fromProcess: payload.fromProcess,
      toProcess: payload.toProcess,
      workOrderNo: payload.workOrderNo,
      qty: payload.qty ?? 1,
      status: '已流转',
      submittedAt: nowTime()
    }
    transferRecords.value.unshift(entry)
    return entry
  }

  const unreadNotificationCount = computed(
    () => workerNotifications.value.filter((n) => !n.read).length
  )

  async function confirmHandover(taskId) {
    const task = pickingTasks.value.find((t) => t.id === taskId)
    if (!task) throw new Error('未找到领料任务')
    if (isTaskHandedOver(task)) throw new Error('该领料任务已完成')
    if (useLiveApi.value) {
      await apiPost('/worker/handover', {
        pickingTaskId: resolvePickingTaskId(task),
        workerId: workerId.value
      })
      await loadWorkerData()
      return true
    }
    task.items.forEach((item) => {
      item.scanned = Math.max(item.scanned, item.required)
      refreshItemStatus(item)
    })
    task.status = '已交接'
    task.handoverTime = nowTime()
    return true
  }

  async function submitException(payload) {
    const task = activeTask.value
    const material = activeMaterial.value
    if (useLiveApi.value) {
      try {
        const entry = await apiPost('/worker/exception', {
          pickingTaskId: resolvePickingTaskId(task),
          pickingLineId: resolvePickingLineId(material),
          workerId: workerId.value,
          type: payload.type,
          note: payload.note,
          location: payload.location
        })
        lastSubmittedException.value = entry
        await loadWorkerData()
        return entry
      } catch (error) {
        throw error
      }
    }
    const entry = {
      id: `ex-${Date.now()}`,
      workOrder: payload.workOrder ?? task?.workOrder ?? '',
      requisition: payload.requisition ?? task?.requisition ?? '',
      material: payload.material ?? material?.material ?? '',
      required: payload.required ?? material?.required ?? 0,
      actual: payload.actual ?? material?.scanned ?? 0,
      shortage: payload.shortage ?? Math.max(0, (material?.required ?? 0) - (material?.scanned ?? 0)),
      type: payload.type,
      note: payload.note ?? '',
      status: '待仓管员补拣',
      submittedAt: nowTime(),
      handler: task?.handler ?? '',
      taskId: task?.id ?? '',
      itemId: material?.id ?? ''
    }
    workerExceptions.value.unshift(entry)
    lastSubmittedException.value = entry
    if (task) task.status = '缺件待补拣'
    return entry
  }

  function openExceptionsInScope(scope) {
    return workerExceptions.value.filter((ex) => {
      if (scope === 'history') return ex.status === '已关闭' || ex.status !== '已关闭'
      if (ex.status === '已关闭') return false
      const task = pickingTasks.value.find((t) => t.workOrder === ex.workOrder)
      if (!task) return scope === 'all-pending' || scope === 'default'
      return matchScope(task, scope)
    })
  }

  function handedOverTasksInScope(scope) {
    return tasksInScope(scope).filter((task) => isTaskHandedOver(task))
  }

  function overviewTasksInScope(scope) {
    return tasksInScope(scope).filter((task) => !task.cancelled)
  }

  function getTaskException(task) {
    return workerExceptions.value.find(
      (ex) => ex.workOrder === task.workOrder && ex.status !== '已关闭'
    )
  }

  function hasOpenException(task) {
    return Boolean(getTaskException(task))
  }

  function needsScan() {
    return false
  }

  function readyForHandover(task) {
    return !isTaskHandedOver(task) && !task.cancelled
  }

  function activePendingTasks() {
    return pickingTasks.value.filter(
      (task) =>
        !task.cancelled &&
        !isTaskHandedOver(task) &&
        (task.planDate === TODAY || isOverdueTask(task))
    )
  }

  function parseExpectedMinutes(time) {
    const [h, m] = (time || '12:00').split(':').map(Number)
    return h * 60 + m
  }

  function priorityScore(task) {
    const od = overdueDays(task.planDate)
    const ex = hasOpenException(task)
    if (od > 0 && ex) return 10000 + od * 100
    if (od > 0) return 9000 + od * 100
    if (ex) return 8000
    return 7000 - parseExpectedMinutes(task.expectedTime)
  }

  function buildPriorityCard(task) {
    const ex = getTaskException(task)
    const od = overdueDays(task.planDate)
    const progress = taskProgress(task)
    const base = {
      task,
      workOrder: task.workOrder,
      requisition: task.requisition,
      headline: task.product,
      meta: `备料 ${progress.scannedItems}/${progress.totalItems} 项 · ${task.expectedTime}`,
      status: displayTaskStatus(task),
      primaryLabel: '确认领料',
      primaryAction: 'handover',
      secondaryLabel: ex ? '查看缺料' : '工单详情',
      secondaryAction: ex ? 'progress' : 'detail'
    }

    if (od > 0 && ex) {
      return {
        ...base,
        kind: 'overdue',
        badge: `逾期 ${od} 天`,
        badgeClass: 'danger',
        headline: `${ex.material}缺 ${ex.shortage} 件`,
        status: '补料/异常'
      }
    }

    if (od > 0) {
      return {
        ...base,
        kind: 'overdue',
        badge: `逾期 ${od} 天`,
        badgeClass: 'danger',
        meta: `计划领料：昨天 ${task.expectedTime}`
      }
    }

    if (ex) {
      return {
        ...base,
        kind: 'shortage',
        badge: '缺料',
        badgeClass: 'warn',
        headline: `${ex.material}缺 ${ex.shortage} 件`,
        status: '补料/异常',
        secondaryLabel: '发起补料'
      }
    }

    return {
      ...base,
      kind: 'pick',
      badge: '待领料',
      badgeClass: 'ok',
      status: '备料区待领'
    }
  }

  const executionQueue = computed(() => {
    const pending = activePendingTasks()
    const sorted = [...pending].sort((a, b) => priorityScore(b) - priorityScore(a))
    const priorityTask = sorted[0] ?? null

    const todayPending = pending.filter((task) => task.planDate === TODAY && task.id !== priorityTask?.id)
    const pickTasks = todayPending.filter((task) => !hasOpenException(task))
    const shortageTasks = todayPending.filter((task) => hasOpenException(task))
    const shortageCount = workerExceptions.value.filter((ex) => ex.status !== '已关闭').length
    const completedTasks = pickingTasks.value.filter((task) => isTaskHandedOver(task))
    const recentCompleted = [...completedTasks]
      .sort((a, b) => (b.handoverTime || '').localeCompare(a.handoverTime || ''))
      .slice(0, 5)

    return {
      summary: {
        pending: pending.length,
        pendingScan: pickTasks.length,
        shortage: shortageCount,
        completed: completedTasks.length
      },
      priority: priorityTask ? buildPriorityCard(priorityTask) : null,
      todayGroups: {
        scan: pickTasks,
        shortage: shortageTasks,
        handover: pickTasks
      },
      recentCompleted
    }
  })

  function pickingStatusClass(status) {
    if (status === '已交接' || status === '已完成') return 'ok'
    if (status === '备料区待领' || status === '备料区' || status === '待领料') return 'ok'
    if (status === '补料/异常' || status === '缺件待补' || status === '缺件待补拣' || status === '缺件') return 'danger'
    if (status === '已取消') return 'warn'
    return 'warn'
  }

  function itemStatusClass(status) {
    if (status === '已备齐' || status === '已确认') return 'ok'
    if (status.startsWith('缺')) return 'danger'
    return 'warn'
  }

  function exceptionStatusClass(status) {
    if (status === '已关闭') return 'ok'
    if (status === '待仓管员补拣' || status === '待仓库确认') return 'danger'
    return 'warn'
  }

  return {
    EXCEPTION_TYPES,
    REPLENISH_REASONS,
    CATEGORY_OPTIONS,
    TODAY,
    pickingTasks,
    scanRecords,
    workerExceptions,
    replenishRecords,
    workerNotifications,
    completionRecords,
    transferRecords,
    unreadNotificationCount,
    activeTaskId,
    activeMaterialId,
    workerDataLoading,
    workerDataError,
    lastScanFeedback,
    lastSubmittedException,
    taskOverview,
    activeTask,
    activeMaterial,
    currentScanProgress,
    taskScanRecords,
    setActiveTask,
    loadWorkerData,
    loadReplenishRecords,
    submitReplenish,
    markNotificationRead,
    submitCompletion,
    submitTransfer,
    scanBarcode: async () => {
      throw new Error('工人端已取消扫码，请使用「确认领料」')
    },
    confirmPickQty: async () => {
      throw new Error('工人端已取消扫码，请使用「确认领料」')
    },
    confirmHandover,
    submitException,
    pickingStatusClass,
    itemStatusClass,
    exceptionStatusClass,
    tasksInScope,
    pendingScanItemsInScope,
    handedOverTasksInScope,
    overviewTasksInScope,
    openExceptionsInScope,
    categoryCounts,
    taskProgress,
    overdueDays,
    isOverdueTask,
    displayTaskStatus,
    formatCount,
    executionQueue,
    getTaskException,
    hasOpenException,
    needsScan,
    readyForHandover,
    isTaskHandedOver,
    workerId,
    setWorkerId,
    syncWorkerIdFromSession,
    resolvePickingTaskId,
    resolvePickingLineId
  }
}
