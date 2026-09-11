import { computed, ref } from 'vue'
import { apiGet, apiPost, apiPostQuery } from '../api'

const pendingRequisitions = ref([])
const outboundOrders = ref([])
const pickingTasks = ref([])
const warehouseExceptions = ref([])
const coordinationNotices = ref([])
const warehouseTip = ref('')
const warehouseDataLoading = ref(false)
const warehouseDataError = ref('')
const lastPendingRequisitionNo = ref('')
const lastGeneratedOutboundId = ref('')
const lastSyncAt = ref('')
const activePickingTaskId = ref('')
const activePickingLineId = ref('')
const lastWarehouseScanFeedback = ref(null)
const workerOptions = ref([])
const workersLoading = ref(false)
const WAREHOUSE_OPERATOR_ID = 2

function statusClass(status) {
  if (['已完成', '已处理', '已扫码', '待交接'].includes(status)) return 'ok'
  if (['异常', '待处理', '库存不足', '数量不一致', '批次不一致', '缺件', '生成失败', '生成失败·待处理'].includes(status)) {
    return 'danger'
  }
  if (['待拣货', '拣货中', '备料区', '待复核', '待生成出库单', '部分扫码', '未扫码'].includes(status)) return 'warn'
  return 'warn'
}

function applyWorkbenchData(data) {
  if (!data) return
  pendingRequisitions.value = (data.pendingRequisitions ?? []).map((row) => ({
    ...row,
    materials: row.materials ?? []
  }))
  outboundOrders.value = (data.outboundOrders ?? []).map((row) => ({
    ...row,
    lines: row.lines ?? [],
    generationLog: row.generationLog ?? []
  }))
  pickingTasks.value = (data.pickingTasks ?? []).map((row) => ({
    ...row,
    progress: row.progress ?? { done: 0, total: 0 },
    lines: row.lines ?? [],
    assigneeName: row.assigneeName ?? (row.assignedTo ? `工人#${row.assignedTo}` : '未分配')
  }))
  warehouseExceptions.value = (data.exceptions ?? []).map((row) => ({ ...row }))
  coordinationNotices.value = (data.coordinationNotices ?? []).map((row) => ({ ...row }))
  lastSyncAt.value = new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

export function useWarehouse() {
  async function loadWorkerOptions() {
    workersLoading.value = true
    try {
      const list = await apiGet('/warehouse/workers')
      workerOptions.value = (Array.isArray(list) ? list : []).map((w) => ({
        id: w.id ?? w.userId,
        name: w.name ?? w.userName ?? `工人#${w.id ?? w.userId}`,
        code: w.code ?? w.userCode ?? '',
        deptName: w.deptName ?? ''
      }))
    } catch {
      workerOptions.value = []
    } finally {
      workersLoading.value = false
    }
  }

  const readyPendingRequisitions = computed(() =>
    pendingRequisitions.value.filter((row) => row.status !== '生成失败·待处理')
  )
  const failedPendingRequisitions = computed(() =>
    pendingRequisitions.value.filter((row) => row.status === '生成失败·待处理')
  )
  const pendingCount = computed(() => readyPendingRequisitions.value.length)
  const failedPendingCount = computed(() => failedPendingRequisitions.value.length)
  const outboundCount = computed(() => outboundOrders.value.length)
  const pickingPendingCount = computed(() => pickingTasks.value.filter((t) => t.status === '待拣货').length)
  const pickingActiveCount = computed(() => pickingTasks.value.filter((t) => t.status === '拣货中').length)
  const pickingPrepCount = computed(() => pickingTasks.value.filter((t) => t.status === '备料区').length)
  const pickingReviewCount = computed(() => pickingTasks.value.filter((t) => t.status === '待复核').length)
  const exceptionOpenCount = computed(() => warehouseExceptions.value.filter((e) => e.status === '待处理').length)
  const coordinationOpenCount = computed(
    () => coordinationNotices.value.filter((n) => n.status !== 'DONE').length
  )

  async function handleCoordinationNotice(noticeId, handledBy = '仓管员') {
    try {
      await apiPost(`/pmc/coordination/notice/${noticeId}/handle`, { handledBy })
      await loadWarehouseData(true)
      warehouseTip.value = `协同通知 #${noticeId} 已标记处理`
      return true
    } catch (error) {
      warehouseTip.value = error.message || '标记协同通知失败'
      return false
    }
  }

  async function readCoordinationNotice(noticeId) {
    try {
      await apiPost(`/pmc/coordination/notice/${noticeId}/read`)
      await loadWarehouseData(true)
      return true
    } catch {
      return false
    }
  }

  const stats = computed(() => [
    ['待生成出库单', String(pendingCount.value)],
    ['生成失败·待处理', String(failedPendingCount.value)],
    ['已生成出库单', String(outboundCount.value)],
    ['待拣货', String(pickingPendingCount.value)],
    ['拣货中', String(pickingActiveCount.value)],
    ['备料区', String(pickingPrepCount.value)],
    ['待复核', String(pickingReviewCount.value)],
    ['待处理异常', String(exceptionOpenCount.value)]
  ])

  async function loadWarehouseData(silent = false) {
    if (!silent) warehouseDataLoading.value = true
    warehouseDataError.value = ''
    try {
      const data = await apiGet('/warehouse/workbench/overview')
      applyWorkbenchData(data)
      if (lastPendingRequisitionNo.value) {
        const stillPending = pendingRequisitions.value.find((row) => row.id === lastPendingRequisitionNo.value)
        if (!stillPending) {
          const generated = outboundOrders.value.find((row) => row.requisition === lastPendingRequisitionNo.value)
          if (generated) {
            lastGeneratedOutboundId.value = generated.id
            lastPendingRequisitionNo.value = ''
            warehouseTip.value = `已生成出库单 ${generated.id}`
          }
        }
      }
    } catch (error) {
      warehouseDataError.value = error.message || '加载仓管数据失败'
    } finally {
      if (!silent) warehouseDataLoading.value = false
    }
  }

  function getPendingRequisition(id) {
    return pendingRequisitions.value.find((row) => row.id === id) ?? null
  }

  function getOutboundOrder(id) {
    return outboundOrders.value.find((row) => row.id === id) ?? null
  }

  function getPickingTask(id) {
    return pickingTasks.value.find((row) => row.id === id) ?? null
  }

  function getException(id) {
    return warehouseExceptions.value.find((row) => row.id === id) ?? null
  }

  function pickingByTab(tab) {
    const map = { pending: '待拣货', picking: '拣货中', prep: '备料区', review: '待复核' }
    const status = map[tab]
    return pickingTasks.value.filter((task) => task.status === status)
  }

  function setActivePickingTask(taskId) {
    activePickingTaskId.value = taskId || ''
    const task = getPickingTask(taskId)
    const nextLine = task?.lines?.find((l) => l.scanStatus !== '已扫码') ?? task?.lines?.[0]
    activePickingLineId.value = nextLine?.id ?? ''
  }

  function setActivePickingLine(lineId) {
    activePickingLineId.value = lineId || ''
  }

  const activePickingTask = computed(() => getPickingTask(activePickingTaskId.value))
  const activePickingLine = computed(() => {
    const task = activePickingTask.value
    if (!task) return null
    return task.lines.find((l) => l.id === activePickingLineId.value) ?? task.lines[0] ?? null
  })

  const activePickingProgress = computed(() => {
    const task = activePickingTask.value
    if (!task) return { done: 0, total: 0, scanned: 0, required: 0, remaining: 0 }
    const total = task.progress?.total ?? task.lines.length
    const done = task.progress?.done ?? task.lines.filter((l) => l.scanStatus === '已扫码').length
    const line = activePickingLine.value
    const required = line?.required ?? 0
    const scanned = line?.picked ?? 0
    return { done, total, scanned, required, remaining: Math.max(0, required - scanned) }
  })

  function setPendingOutboundRequisition(requisitionNo) {
    lastPendingRequisitionNo.value = requisitionNo || ''
  }

  /** Agent 出库成功后：从数据库重新加载，不再本地伪造单据 */
  async function syncOutboundFromServer(requisitionNo) {
    const reqNo = requisitionNo || lastPendingRequisitionNo.value
    await loadWarehouseData(true)
    if (!reqNo) return null
    const order = outboundOrders.value.find((row) => row.requisition === reqNo)
    if (order) {
      lastGeneratedOutboundId.value = order.id
      lastPendingRequisitionNo.value = ''
      warehouseTip.value = `已生成出库单 ${order.id}${order.pickingTaskId ? `，拣货任务 ${order.pickingTaskId}` : ''}`
      return { order, picking: getPickingTask(order.pickingTaskId) }
    }
    return null
  }

  async function startWarehousePicking(taskId, operatorId = WAREHOUSE_OPERATOR_ID) {
    const task = getPickingTask(taskId)
    if (!task?.pickingTaskId) {
      warehouseTip.value = '缺少拣货任务 ID'
      return false
    }
    try {
      await apiPostQuery('/warehouse/picking/start', {
        pickingTaskId: task.pickingTaskId,
        operatorId
      })
      warehouseTip.value = `已开始 PDA 拣货 · ${taskId}`
      await loadWarehouseData(true)
      setActivePickingTask(taskId)
      await fetchPrepRecommend(taskId)
      return true
    } catch (error) {
      warehouseTip.value = error.message || '开始拣货失败'
      return false
    }
  }

  async function submitWarehouseScan({ barcodeValue, qty = 1, operatorId = WAREHOUSE_OPERATOR_ID } = {}) {
    const task = activePickingTask.value
    const line = activePickingLine.value
    if (!task?.pickingTaskId || !line?.pickingLineId) {
      warehouseTip.value = '请先选择拣货任务与物料行'
      return null
    }
    try {
      const result = await apiPost('/warehouse/picking/scan', {
        pickingTaskId: task.pickingTaskId,
        pickingLineId: line.pickingLineId,
        operatorId,
        barcodeValue,
        qty
      })
      lastWarehouseScanFeedback.value = result
      await loadWarehouseData(true)
      setActivePickingTask(task.id)
      if (result?.success) {
        warehouseTip.value = `${line.name} 扫码成功`
      }
      return result
    } catch (error) {
      lastWarehouseScanFeedback.value = { success: false, messages: [error.message || '扫码失败'] }
      warehouseTip.value = error.message || '扫码失败'
      return null
    }
  }

  async function completeWarehousePicking(taskId, targetWorkerId = 1, prepLocationId = null, operatorId = WAREHOUSE_OPERATOR_ID) {
    const task = getPickingTask(taskId)
    if (!task?.pickingTaskId) {
      warehouseTip.value = '缺少拣货任务 ID'
      return false
    }
    try {
      const body = {
        pickingTaskId: task.pickingTaskId,
        operatorId,
        targetWorkerId
      }
      if (prepLocationId) body.prepLocationId = prepLocationId
      await apiPost('/warehouse/picking/complete', body)
      const workerName = workerOptions.value.find((w) => w.id === targetWorkerId)?.name || `工人#${targetWorkerId}`
      const prepCode =
        prepRecommend.value?.options?.find((o) => o.locationId === prepLocationId)?.locationCode ||
        prepRecommend.value?.locationCode ||
        '备料区'
      warehouseTip.value = `拣货完成，物料已入 ${prepCode}，已通知 ${workerName} 领取`
      prepRecommend.value = null
      await loadWarehouseData(true)
      return true
    } catch (error) {
      warehouseTip.value = error.message || '完成拣货失败'
      return false
    }
  }

  const prepRecommend = ref(null)
  const prepRecommendLoading = ref(false)

  async function fetchPrepRecommend(taskId) {
    const task = getPickingTask(taskId)
    if (!task?.pickingTaskId || task.status !== '拣货中') {
      prepRecommend.value = null
      return null
    }
    prepRecommendLoading.value = true
    try {
      const data = await apiGet('/warehouse/prep/recommend', { pickingTaskId: task.pickingTaskId })
      prepRecommend.value = data
      return data
    } catch (error) {
      prepRecommend.value = null
      warehouseTip.value = error.message || '获取备料库位推荐失败'
      return null
    } finally {
      prepRecommendLoading.value = false
    }
  }

  /** @deprecated 旧流程：分配工人到库内拣货，已由仓管 PDA 拣货替代 */
  async function assignPickingTask(taskId, workerId = 1) {
    return startWarehousePicking(taskId)
  }

  async function startPicking(taskId) {
    return startWarehousePicking(taskId)
  }

  async function confirmScanLine(taskId, lineId, barcodeValue, qty = 1) {
    setActivePickingTask(taskId)
    setActivePickingLine(lineId)
    return submitWarehouseScan({ barcodeValue, qty })
  }

  function reportPickingException(taskId, lineId, note) {
    const task = getPickingTask(taskId)
    if (!task) return null
    const line = task.lines.find((l) => l.id === lineId) ?? task.lines[0]
    const ex = {
      id: `LOCAL-${Date.now()}`,
      relatedDoc: task.outbound,
      relatedType: 'outbound',
      stage: '拣货',
      type: '数量不一致',
      description: note || `${line?.name ?? '物料'}实拣异常`,
      material: line?.name ?? '',
      suggestion: '核对库位实物与推荐批次，确认后补拣或登记短缺。',
      result: '',
      status: '待处理',
      createdAt: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    }
    warehouseExceptions.value = [ex, ...warehouseExceptions.value]
    warehouseTip.value = `已上报异常 ${ex.id}`
    return ex
  }

  async function completePicking(taskId, targetWorkerId = 1) {
    return completeWarehousePicking(taskId, targetWorkerId)
  }

  async function reviewPicking(taskId, pass = true) {
    const task = getPickingTask(taskId)
    if (!task || task.status !== '待复核') return null
    if (!task.outboundId) {
      warehouseTip.value = '缺少出库单 ID'
      return null
    }
    try {
      const reviewTask = await apiPost(`/outbound/review/create?outboundId=${task.outboundId}&reviewedBy=1`)
      await apiPost('/outbound/review/submit', {
        reviewTaskId: reviewTask.reviewTaskId,
        reviewedBy: 1,
        lines: (task.lines ?? []).map((line) => ({
          pickingLineId: line.pickingLineId,
          reviewQty: line.picked ?? line.required,
          reviewResult: pass ? 'PASS' : 'EXCEPTION',
          exceptionType: pass ? null : '批次不一致',
          exceptionDesc: pass ? null : '复核发现扫码批次与推荐批次不一致'
        }))
      })
      await loadWarehouseData(true)
      if (pass) {
        warehouseTip.value = `复核通过，出库单 ${task.outbound} 进入待交接`
        return { ok: true }
      }
      warehouseTip.value = '复核异常已登记'
      return { ok: false }
    } catch (error) {
      warehouseTip.value = error.message || '复核提交失败'
      return null
    }
  }

  async function resolveException(exceptionId, result = '已标记处理完成，可重新生成出库单') {
    const ex = getException(exceptionId)
    if (!ex || ex.status === '已处理') return false
    try {
      if (String(exceptionId).startsWith('OEX') || ex.source === 'GENERATE') {
        await apiPost(`/warehouse/exception/${exceptionId}/resolve`, { result })
        await loadWarehouseData(true)
        warehouseTip.value = `异常 ${exceptionId} 已标记处理，可回出库单页重新生成`
        return true
      }
      // 工人/复核异常暂无统一关闭接口，保留本地标记
      ex.status = '已处理'
      ex.result = result
      warehouseTip.value = `异常 ${exceptionId} 已标记处理`
      return true
    } catch (error) {
      warehouseTip.value = error.message || '标记异常失败'
      return false
    }
  }

  return {
    pendingRequisitions,
    readyPendingRequisitions,
    failedPendingRequisitions,
    outboundOrders,
    pickingTasks,
    warehouseExceptions,
    coordinationNotices,
    coordinationOpenCount,
    handleCoordinationNotice,
    readCoordinationNotice,
    warehouseTip,
    warehouseDataLoading,
    warehouseDataError,
    lastPendingRequisitionNo,
    lastGeneratedOutboundId,
    lastSyncAt,
    stats,
    pendingCount,
    failedPendingCount,
    outboundCount,
    pickingPendingCount,
    pickingActiveCount,
    pickingPrepCount,
    pickingReviewCount,
    exceptionOpenCount,
    activePickingTaskId,
    activePickingLineId,
    activePickingTask,
    activePickingLine,
    activePickingProgress,
    lastWarehouseScanFeedback,
    statusClass,
    loadWarehouseData,
    getPendingRequisition,
    getOutboundOrder,
    getPickingTask,
    getException,
    pickingByTab,
    setPendingOutboundRequisition,
    syncOutboundFromServer,
    startPicking,
    startWarehousePicking,
    submitWarehouseScan,
    completeWarehousePicking,
    prepRecommend,
    prepRecommendLoading,
    fetchPrepRecommend,
    assignPickingTask,
    workerOptions,
    workersLoading,
    loadWorkerOptions,
    setActivePickingTask,
    setActivePickingLine,
    confirmScanLine,
    reportPickingException,
    completePicking,
    reviewPicking,
    resolveException
  }
}
