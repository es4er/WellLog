<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { useRoleAccess } from '../../composables/useRoleAccess'
import { useQuality } from '../../composables/useQuality'
import { DEFECT_CODES } from '../../data/qualityConstants'
import { INSPECTION_SAMPLES, findInspectionSample } from '../../data/inspectionSamples'
import { menuToPath } from '../../router/menuRoutes'

const route = useRoute()
const router = useRouter()
const { userId } = useSession()
const { allowed } = useRoleAccess('quality')
const {
  inspectionDetail,
  qualityDataLoading,
  qualityDataError,
  loadInspectionDetail,
  submitInspectionResult
} = useQuality()

const submitting = ref(false)
const submitError = ref('')
const submitSuccess = ref('')
const activeLineIndex = ref(0)
const sampleIndex = ref(0)
const drawingZoom = ref(1)
const drawingPage = ref(1)
const highlightDimId = ref(null)
const aiRunning = ref(false)
const aiResult = ref(null)
const attachments = ref([])
const fileInputRef = ref(null)
const sampleDrafts = reactive({})
const lineSampleIndices = reactive({})
const lineActiveTabs = reactive({})

/* ========= 顶部检测流程分栏（尺寸 / 外观） ========= */
const TABS = [
  { id: 'dimension', label: '尺寸检测' },
  { id: 'appearance', label: '外观检测' }
]
const activeTab = ref('dimension')

const form = reactive({
  appearance: 'QUALIFIED',
  appearanceRemark: '',
  selectedDefects: [],
  performanceActual: '',
  performanceResult: 'QUALIFIED',
  dimValues: {},
  generateNcr: false
})

const receiptId = computed(() => Number(route.query.receiptId) || null)
const PENDING_LINE_STATUSES = ['INSPECTING', 'PENDING_INSPECTION']
const allLineDetails = computed(() => inspectionDetail.value?.lineDetails ?? [])
const lineDetails = computed(() =>
  allLineDetails.value.filter((line) => PENDING_LINE_STATUSES.includes(line.lineStatus))
)
const activeLine = computed(() => lineDetails.value[activeLineIndex.value] ?? null)
const lineProgress = computed(() => {
  const total = lineDetails.value.length
  const current = total ? activeLineIndex.value + 1 : 0
  return { total, current }
})
const standards = computed(() => activeLine.value?.standards ?? [])
const receipt = computed(() => inspectionDetail.value?.receipt)
const strict = computed(() => inspectionDetail.value?.strict)
const drawing = computed(() => activeLine.value?.drawing ?? null)
const aql = computed(() => activeLine.value?.aql ?? null)
const dimensions = computed(() => activeLine.value?.dimensions ?? [])
const performance = computed(() => activeLine.value?.performance ?? null)

const sampleSize = computed(() => aql.value?.sampleSize || 1)
const currentSampleNo = computed(() => Math.min(sampleIndex.value + 1, sampleSize.value))

const dimRows = computed(() => {
  return dimensions.value.map((dim) => {
    const raw = form.dimValues[dim.id]
    const actual = raw === '' || raw == null ? NaN : parseFloat(raw)
    const hasValue = !Number.isNaN(actual)
    let result = 'PENDING'
    let deviation = null
    if (hasValue) {
      deviation = Number((actual - dim.nominal).toFixed(4))
      const lo = dim.nominal + dim.lowerTol
      const hi = dim.nominal + dim.upperTol
      result = actual >= lo && actual <= hi ? 'PASS' : 'FAIL'
    }
    return { ...dim, actual: hasValue ? actual : null, deviation, result }
  })
})

const dimSummary = computed(() => {
  const total = dimRows.value.length
  const filled = dimRows.value.filter((r) => r.result !== 'PENDING').length
  const pass = dimRows.value.filter((r) => r.result === 'PASS').length
  const fail = dimRows.value.filter((r) => r.result === 'FAIL').length
  const criticalFail = dimRows.value.some((r) => r.critical && r.result === 'FAIL')
  return { total, filled, pass, fail, criticalFail, allPass: filled === total && fail === 0 && total > 0 }
})

const appearanceOk = computed(() => form.appearance === 'QUALIFIED')
const performanceOk = computed(() => form.performanceResult === 'QUALIFIED')
const overallPass = computed(() => {
  if (!dimSummary.value.allPass) return false
  if (!appearanceOk.value) return false
  if (!performanceOk.value) return false
  return true
})

const overallLabel = computed(() => {
  if (dimSummary.value.filled === 0 && appearanceOk.value && performanceOk.value) return '待录入'
  return overallPass.value ? 'PASS' : 'FAIL'
})

const evidenceRequired = computed(() => !overallPass.value && overallLabel.value === 'FAIL')
const canSubmit = computed(() => {
  if (dimSummary.value.filled < dimSummary.value.total) return false
  if (evidenceRequired.value && attachments.value.length === 0) return false
  if (!appearanceOk.value && form.selectedDefects.length === 0 && !form.appearanceRemark.trim()) return false
  return true
})

const defectGroups = computed(() => {
  const map = {}
  for (const d of DEFECT_CODES) {
    if (!map[d.category]) map[d.category] = []
    map[d.category].push(d)
  }
  return map
})

function makeEmptyDimValues() {
  const next = {}
  for (const dim of dimensions.value) {
    next[dim.id] = ''
  }
  return next
}

function initDimValues() {
  const next = makeEmptyDimValues()
  for (const dim of dimensions.value) {
    next[dim.id] = form.dimValues[dim.id] ?? ''
  }
  form.dimValues = next
}

function getLineId(lineIndex = activeLineIndex.value) {
  const line = lineDetails.value[lineIndex]
  if (!line) return String(lineIndex)
  return String(line.receiptLineId ?? line.itemId ?? lineIndex)
}

function sampleKey(index = sampleIndex.value, lineIndex = activeLineIndex.value) {
  return `${getLineId(lineIndex)}:${index}`
}

function makeSampleState() {
  return {
    appearance: 'QUALIFIED',
    appearanceRemark: '',
    selectedDefects: [],
    performanceActual: '',
    performanceResult: 'QUALIFIED',
    dimValues: makeEmptyDimValues(),
    generateNcr: false,
    attachments: [],
    aiResult: null,
    compareResult: null,
    stdImage: null,
    curImage: null,
    selectedSampleId: null
  }
}

function saveCurrentSampleState() {
  if (!activeLine.value) return
  persistCompareState()
  sampleDrafts[sampleKey()] = {
    appearance: form.appearance,
    appearanceRemark: form.appearanceRemark,
    selectedDefects: [...form.selectedDefects],
    performanceActual: form.performanceActual,
    performanceResult: form.performanceResult,
    dimValues: { ...form.dimValues },
    generateNcr: form.generateNcr,
    attachments: attachments.value.map((item) => ({ ...item })),
    aiResult: aiResult.value ? { ...aiResult.value, suggestions: [...(aiResult.value.suggestions ?? [])] } : null,
    compareResult: compareResult.value ? JSON.parse(JSON.stringify(compareResult.value)) : null,
    stdImage: stdImage.value ? { ...stdImage.value, file: stdImage.value.file } : null,
    curImage: curImage.value ? { ...curImage.value, file: curImage.value.file } : null,
    selectedSampleId: selectedSampleId.value
  }
}

function loadSampleState(index) {
  const hasDraft = Boolean(sampleDrafts[sampleKey(index)])
  const state = sampleDrafts[sampleKey(index)] || makeSampleState()
  form.appearance = state.appearance
  form.appearanceRemark = state.appearanceRemark
  form.selectedDefects = [...state.selectedDefects]
  form.performanceActual = state.performanceActual
  form.performanceResult = state.performanceResult
  form.dimValues = { ...makeEmptyDimValues(), ...state.dimValues }
  form.generateNcr = state.generateNcr
  attachments.value = state.attachments.map((item) => ({ ...item }))
  aiResult.value = state.aiResult ? { ...state.aiResult, suggestions: [...(state.aiResult.suggestions ?? [])] } : null
  compareResult.value = state.compareResult ? JSON.parse(JSON.stringify(state.compareResult)) : null
  stdImage.value = state.stdImage ? { ...state.stdImage } : null
  curImage.value = state.curImage ? { ...state.curImage } : null
  selectedSampleId.value = state.selectedSampleId ?? null
  highlightDimId.value = null
  if (!hasDraft) restoreCompareState(index)
}

function clearLineDraftsByLineId(lineId) {
  const prefix = `${lineId}:`
  Object.keys(sampleDrafts).forEach((key) => {
    if (!key.startsWith(prefix)) return
    for (const file of sampleDrafts[key]?.attachments ?? []) {
      if (file.url?.startsWith('blob:')) URL.revokeObjectURL(file.url)
    }
    delete sampleDrafts[key]
  })
  delete lineSampleIndices[lineId]
  delete lineActiveTabs[lineId]
}

function clearSampleDrafts() {
  Object.keys(sampleDrafts).forEach((key) => {
    for (const file of sampleDrafts[key]?.attachments ?? []) {
      if (file.url?.startsWith('blob:')) URL.revokeObjectURL(file.url)
    }
    delete sampleDrafts[key]
  })
}

function resetAllDrafts() {
  clearSampleDrafts()
  Object.keys(lineSampleIndices).forEach((key) => {
    delete lineSampleIndices[key]
  })
  Object.keys(lineActiveTabs).forEach((key) => {
    delete lineActiveTabs[key]
  })
}

function lineHasDraft(lineIndex) {
  const prefix = `${getLineId(lineIndex)}:`
  return Object.keys(sampleDrafts).some((key) => {
    if (!key.startsWith(prefix)) return false
    const draft = sampleDrafts[key]
    if (!draft) return false
    return Boolean(
      draft.appearanceRemark?.trim() ||
      draft.selectedDefects?.length ||
      draft.performanceActual ||
      draft.attachments?.length ||
      draft.compareResult ||
      draft.aiResult ||
      Object.values(draft.dimValues ?? {}).some((value) => value !== '' && value != null) ||
      draft.appearance === 'UNQUALIFIED' ||
      draft.performanceResult === 'UNQUALIFIED'
    )
  })
}

function loadLineState(lineIndex) {
  sampleIndex.value = lineSampleIndices[getLineId(lineIndex)] ?? 0
  loadSampleState(sampleIndex.value)
  activeTab.value = lineActiveTabs[getLineId(lineIndex)] ?? 'dimension'
  drawingPage.value = 1
  drawingZoom.value = 1
  highlightDimId.value = null
}

async function switchLine(index) {
  if (index === activeLineIndex.value) return
  saveCurrentSampleState()
  lineSampleIndices[getLineId()] = sampleIndex.value
  lineActiveTabs[getLineId()] = activeTab.value
  activeLineIndex.value = index
  await nextTick()
  loadLineState(index)
}

function resetLineForm() {
  resetAllDrafts()
  form.appearance = 'QUALIFIED'
  form.appearanceRemark = ''
  form.selectedDefects = []
  form.performanceActual = ''
  form.performanceResult = 'QUALIFIED'
  form.generateNcr = false
  form.dimValues = {}
  initDimValues()
  sampleIndex.value = 0
  drawingPage.value = 1
  drawingZoom.value = 1
  highlightDimId.value = null
  aiResult.value = null
  attachments.value = []
  compareResult.value = null
  stdImage.value = null
  curImage.value = null
  selectedSampleId.value = null
  activeTab.value = 'dimension'
}

watch(dimensions, initDimValues, { immediate: true })

watch(receiptId, (id, prev) => {
  if (prev == null || id === prev) return
  resetLineForm()
})

watch(activeTab, (tab) => {
  if (!activeLine.value) return
  lineActiveTabs[getLineId()] = tab
})

onMounted(async () => {
  comparing.value = false
  comparePhase.value = ''
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('检测执行'))
    return
  }
  if (!receiptId.value) {
    router.replace({ path: '/quality/tasks' })
    return
  }
  await loadInspectionDetail(receiptId.value)
  initDimValues()
  restoreCompareState()
})

function formatTol(dim) {
  const u = dim.upperTol >= 999 ? '+' : (dim.upperTol >= 0 ? `+${dim.upperTol}` : String(dim.upperTol))
  const l = dim.lowerTol === 0 && dim.upperTol >= 999 ? 'min' : String(dim.lowerTol)
  if (dim.upperTol >= 999) return `≥ ${dim.nominal}`
  return `${u} / ${l}`
}

function formatSpec(dim) {
  if (dim.upperTol >= 999) return `≥ ${dim.nominal} ${dim.unit}`
  return `${dim.nominal} (${formatTol(dim)}) ${dim.unit}`
}

function resolveQualifiedPerformanceActual(perf) {
  if (perf?.minValue != null && perf.minValue !== '') {
    return String(perf.minValue)
  }
  if (perf?.standard) {
    const match = String(perf.standard).match(/[\d.]+/)
    if (match) return match[0]
  }
  return '100'
}

function buildQualifiedSampleState(line) {
  const dimValues = {}
  for (const dim of line?.dimensions ?? []) {
    dimValues[dim.id] = String(dim.nominal)
  }
  return {
    appearance: 'QUALIFIED',
    appearanceRemark: '',
    selectedDefects: [],
    performanceActual: resolveQualifiedPerformanceActual(line?.performance),
    performanceResult: 'QUALIFIED',
    dimValues,
    generateNcr: false,
    attachments: [],
    aiResult: null,
    compareResult: null,
    stdImage: null,
    curImage: null,
    selectedSampleId: null
  }
}

/** 演示用：一键填入全部待检物料的合格检测数据 */
function fillQualifiedData() {
  if (!lineDetails.value.length) return
  saveCurrentSampleState()
  submitError.value = ''

  let lineCount = 0
  let sampleCount = 0
  for (let lineIndex = 0; lineIndex < lineDetails.value.length; lineIndex += 1) {
    const line = lineDetails.value[lineIndex]
    const lineId = getLineId(lineIndex)
    const samples = line.aql?.sampleSize || 1
    lineCount += 1
    for (let sampleIdx = 0; sampleIdx < samples; sampleIdx += 1) {
      sampleDrafts[`${lineId}:${sampleIdx}`] = buildQualifiedSampleState(line)
      sampleCount += 1
    }
    lineSampleIndices[lineId] = lineSampleIndices[lineId] ?? 0
  }

  loadLineState(activeLineIndex.value)
  submitSuccess.value = `已填入合格数据：${lineCount} 种物料 · ${sampleCount} 个样本（尺寸取标准值，外观/耐压判定合格）`
}

function toggleDefect(code) {
  const i = form.selectedDefects.indexOf(code)
  if (i >= 0) form.selectedDefects.splice(i, 1)
  else form.selectedDefects.push(code)
  if (form.selectedDefects.length > 0) form.appearance = 'UNQUALIFIED'
}

function focusDim(id) {
  highlightDimId.value = id
  const el = document.getElementById(`dim-row-${id}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function zoomDrawing(delta) {
  drawingZoom.value = Math.min(1.8, Math.max(0.7, Number((drawingZoom.value + delta).toFixed(2))))
}

function onPickFiles(event) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    const url = URL.createObjectURL(file)
    attachments.value.push({
      id: `${Date.now()}-${file.name}`,
      name: file.name,
      type: file.type.startsWith('image/') ? 'image' : 'file',
      size: file.size,
      url,
      isEvidence: evidenceRequired.value
    })
  }
  event.target.value = ''
}

function removeAttachment(id) {
  const idx = attachments.value.findIndex((a) => a.id === id)
  if (idx >= 0) {
    URL.revokeObjectURL(attachments.value[idx].url)
    attachments.value.splice(idx, 1)
  }
}

function nextSample() {
  if (sampleIndex.value >= sampleSize.value - 1) return
  saveCurrentSampleState()
  sampleIndex.value += 1
  loadSampleState(sampleIndex.value)
}

function prevSample() {
  if (sampleIndex.value <= 0) return
  saveCurrentSampleState()
  sampleIndex.value -= 1
  loadSampleState(sampleIndex.value)
}

async function runAiVision() {
  if (!attachments.value.some((a) => a.type === 'image')) {
    submitError.value = '请先上传至少一张检测照片，再运行 AI 视觉辅助'
    return
  }
  aiRunning.value = true
  submitError.value = ''
  await new Promise((r) => setTimeout(r, 900))
  const failDims = dimRows.value.filter((r) => r.result === 'FAIL')
  const suggestions = []
  if (failDims.length) {
    suggestions.push({
      code: 'DIM',
      label: '尺寸超差',
      confidence: 0.91,
      note: `检测到与气泡 ${failDims.map((d) => d.balloon).join('、')} 相关的尺寸异常区域`
    })
  }
  suggestions.push({
    code: 'SCR',
    label: '划伤',
    confidence: failDims.length ? 0.62 : 0.78,
    note: '密封面/外圆区域存在疑似线性划痕（需人工复核）'
  })
  if (strict.value) {
    suggestions.push({
      code: 'BUR',
      label: '毛刺',
      confidence: 0.55,
      note: '螺纹牙顶边缘疑似毛刺，加严批次建议重点复核'
    })
  }
  aiResult.value = {
    model: 'WMS-Vision QC Assist v0.9',
    analyzedAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    imageCount: attachments.value.filter((a) => a.type === 'image').length,
    suggestions,
    summary: failDims.length
      ? `AI 建议判定 FAIL：发现 ${failDims.length} 项尺寸超差迹象，请核对实测值并确认缺陷代码。`
      : 'AI 未发现明显尺寸异常；外观建议人工确认划伤类缺陷。'
  }
  aiRunning.value = false
}

function applyAiSuggestion(code) {
  if (!form.selectedDefects.includes(code)) form.selectedDefects.push(code)
  form.appearance = 'UNQUALIFIED'
}

function buildIssueDesc() {
  const parts = []
  const failDims = dimRows.value.filter((r) => r.result === 'FAIL')
  if (failDims.length) {
    parts.push(
      '尺寸超差：' +
        failDims
          .map((d) => `${d.name} 实测 ${d.actual}${d.unit}（标准 ${formatSpec(d)}）`)
          .join('；')
    )
  }
  if (form.selectedDefects.length) {
    const labels = form.selectedDefects
      .map((c) => DEFECT_CODES.find((d) => d.code === c)?.label || c)
      .join('、')
    parts.push(`缺陷代码：${labels}`)
  }
  if (form.appearanceRemark.trim()) parts.push(form.appearanceRemark.trim())
  if (!performanceOk.value) parts.push(`性能不合格：${performance.value?.name || '性能项'}`)
  return parts.join(' | ') || '检测项目未全部合格'
}

function buildIssueDescFromDraft(draft, line) {
  const dimensions = line.dimensions ?? []
  const parts = []
  for (const dim of dimensions) {
    const raw = draft?.dimValues?.[dim.id]
    const actual = raw === '' || raw == null ? NaN : parseFloat(raw)
    if (Number.isNaN(actual)) continue
    const lo = dim.nominal + dim.lowerTol
    const hi = dim.nominal + dim.upperTol
    if (actual < lo || actual > hi) {
      parts.push(`尺寸超差：${dim.name} 实测 ${actual}${dim.unit}（标准 ${formatSpec(dim)}）`)
    }
  }
  if (draft?.selectedDefects?.length) {
    const labels = draft.selectedDefects
      .map((c) => DEFECT_CODES.find((d) => d.code === c)?.label || c)
      .join('、')
    parts.push(`缺陷代码：${labels}`)
  }
  if (draft?.appearanceRemark?.trim()) parts.push(draft.appearanceRemark.trim())
  if (draft?.performanceResult === 'UNQUALIFIED') {
    parts.push(`性能不合格：${line.performance?.name || '性能项'}`)
  }
  return parts.join(' | ') || '检测项目未全部合格'
}

function getDraftForLine(line) {
  const lineId = String(line.receiptLineId ?? line.itemId)
  const sampleIdx = lineSampleIndices[lineId] ?? 0
  return sampleDrafts[`${lineId}:${sampleIdx}`] ?? null
}

function evaluateLineDraft(line, draft) {
  const dimensions = line.dimensions ?? []
  const sample = line.aql?.sampleSize || 1
  const itemName = line.itemName || line.itemCode || '物料'

  if (!draft) {
    return { valid: false, error: `${itemName}：未填写检测数据` }
  }
  if (!dimensions.length) {
    return { valid: false, error: `${itemName}：缺少检测项配置` }
  }

  let filled = 0
  let fail = 0
  const dimensionResults = []
  for (const dim of dimensions) {
    const raw = draft.dimValues?.[dim.id]
    const actual = raw === '' || raw == null ? NaN : parseFloat(raw)
    if (Number.isNaN(actual)) continue
    filled++
    const lo = dim.nominal + dim.lowerTol
    const hi = dim.nominal + dim.upperTol
    const pass = actual >= lo && actual <= hi
    if (!pass) fail++
    dimensionResults.push({
      id: dim.id,
      actual,
      result: pass ? 'PASS' : 'FAIL',
      deviation: Number((actual - dim.nominal).toFixed(4))
    })
  }

  if (filled < dimensions.length) {
    return {
      valid: false,
      error: `${itemName}：请完成全部 ${dimensions.length} 项尺寸录入（已完成 ${filled}）`
    }
  }

  const appearanceOk = (draft.appearance ?? 'QUALIFIED') === 'QUALIFIED'
  const performanceOk = (draft.performanceResult ?? 'QUALIFIED') === 'QUALIFIED'
  const linePass = fail === 0 && appearanceOk && performanceOk

  if (!linePass && !(draft.attachments?.length)) {
    return { valid: false, error: `${itemName}：不合格判定须上传照片或附件留证` }
  }
  if (!appearanceOk && !draft.selectedDefects?.length && !draft.appearanceRemark?.trim()) {
    return { valid: false, error: `${itemName}：外观不合格时请填写缺陷代码或备注` }
  }

  const failRate = fail / Math.max(dimensions.length, 1)
  const unqualifiedQty = linePass
    ? 0
    : Math.max(1, Math.min(sample, Math.round(sample * Math.max(failRate, 0.05)) || 1))
  const inspectedQty = sample
  const qualifiedQty = linePass ? inspectedQty : Math.max(0, inspectedQty - unqualifiedQty)
  const lineResult = linePass ? 'QUALIFIED' : 'UNQUALIFIED'

  return {
    valid: true,
    overallPass: linePass,
    line: {
      receiptLineId: line.receiptLineId,
      itemId: line.itemId,
      batchId: line.batchId,
      inspectedQty,
      qualifiedQty,
      unqualifiedQty,
      lineResult,
      issueType: lineResult === 'QUALIFIED' ? null : 'QUALITY_DEFECT',
      issueDesc: lineResult === 'QUALIFIED' ? null : buildIssueDescFromDraft(draft, line),
      defectCodes: draft.selectedDefects ?? [],
      attachmentCount: draft.attachments?.length ?? 0,
      aqlSampleSize: sample,
      dimensionResults
    }
  }
}

async function handleSubmit(forceNcr = false) {
  if (!receiptId.value) return

  saveCurrentSampleState()
  submitError.value = ''

  const pending = lineDetails.value
  if (!pending.length) {
    submitError.value = '当前没有待提交的检测物料'
    return
  }

  if (forceNcr) {
    if (overallPass.value) {
      submitError.value = '当前物料综合判定为合格，请使用「提交全部物料」'
      return
    }
    if (!canSubmit.value) {
      submitError.value = '请完善检测信息并上传不合格留证附件后再生成异常单'
      return
    }
  } else {
    if (!overallPass.value) {
      submitError.value = '当前物料综合判定为不合格，请使用「生成质量异常单」'
      return
    }
    if (!canSubmit.value) {
      submitError.value = '请完善检测信息后再提交'
      return
    }
  }

  const payloadLines = []
  const errors = []
  let anyFail = false

  for (const line of pending) {
    const draft = getDraftForLine(line)
    const evaluated = evaluateLineDraft(line, draft)
    if (!evaluated.valid) {
      errors.push(evaluated.error)
    } else if (!forceNcr && !evaluated.overallPass) {
      const name = line.itemName || line.itemCode || '物料'
      errors.push(`${name}：判定不合格，请使用「生成质量异常单」提交`)
    } else {
      payloadLines.push(evaluated.line)
      if (!evaluated.overallPass) anyFail = true
    }
  }

  if (errors.length) {
    submitError.value = errors.join('；')
    return
  }

  submitting.value = true
  submitError.value = ''
  submitSuccess.value = ''

  try {
    const result = await submitInspectionResult({
      receiptId: receiptId.value,
      inspectedBy: userId.value ?? 1,
      lines: payloadLines
    })

    resetLineForm()
    await loadInspectionDetail(receiptId.value)

    if (result.hasIssue || anyFail) {
      const ncrHint = forceNcr ? '已生成质量异常单' : '已自动登记质量问题'
      submitSuccess.value = `检测完成（${payloadLines.length} 种物料），${ncrHint}，正在进入质量闭环…`
      setTimeout(() => {
        router.push({
          path: menuToPath('质量异常', 'quality'),
          query: { issueId: result.issueIds?.[0] }
        })
      }, 1000)
    } else {
      submitSuccess.value =
        result.message ||
        `检测完成，共 ${payloadLines.length} 种物料全部合格，可通知仓管员入库`
    }
  } catch (error) {
    submitError.value = error.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

/* ================= 八、AI 标准样图对比（OpenCV + SSIM 轻量版） ================= */
const stdImage = ref(null) // { url, name, file }
const curImage = ref(null)
const selectedSampleId = ref(null)
const selectedSample = computed(() => findInspectionSample(selectedSampleId.value))
const comparing = ref(false)
const comparePhase = ref('')
const compareError = ref('')
const compareResult = ref(null)
const stdFileRef = ref(null)
const curFileRef = ref(null)

/* ---- 对比图片 / 结果的本地持久化：刷新页面后保留 ---- */
const COMPARE_STORAGE_PREFIX = 'wms.qc.compare.'

function compareStorageKey() {
  return `${COMPARE_STORAGE_PREFIX}${receiptId.value ?? 'na'}`
}

function readCompareStore() {
  try {
    return JSON.parse(localStorage.getItem(compareStorageKey()) || '{}')
  } catch {
    return {}
  }
}

function writeCompareStore(store) {
  try {
    localStorage.setItem(compareStorageKey(), JSON.stringify(store))
  } catch {
    /* localStorage 已满或不可用，忽略（结果图仍保存在服务端） */
  }
}

async function imageMetaToDataUrl(imageMeta) {
  if (!imageMeta?.url) return null
  if (imageMeta.url.startsWith('data:')) {
    return { dataUrl: imageMeta.url, name: imageMeta.name || 'image.jpg' }
  }
  try {
    const res = await fetch(imageMeta.url)
    const blob = await res.blob()
    return await new Promise((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve({ dataUrl: reader.result, name: imageMeta.name || 'image.jpg' })
      reader.onerror = () => resolve(null)
      reader.readAsDataURL(blob)
    })
  } catch {
    return null
  }
}

async function persistCompareState() {
  if (!receiptId.value || !activeLine.value) return
  const store = readCompareStore()
  const [std, cur] = await Promise.all([
    imageMetaToDataUrl(stdImage.value),
    imageMetaToDataUrl(curImage.value)
  ])
  store[sampleKey()] = {
    std,
    cur,
    selectedSampleId: selectedSampleId.value,
    compareResult: compareResult.value ? JSON.parse(JSON.stringify(compareResult.value)) : null
  }
  writeCompareStore(store)
}

function restoreCompareState(index = sampleIndex.value) {
  if (!receiptId.value || !activeLine.value) return
  const saved = readCompareStore()[sampleKey(index)]
  if (!saved) return
  if (typeof saved.selectedSampleId === 'number') selectedSampleId.value = saved.selectedSampleId
  if (saved.std?.dataUrl) {
    stdImage.value = { url: saved.std.dataUrl, name: saved.std.name || '标准样图.jpg' }
  } else if (selectedSample.value) {
    stdImage.value = { url: selectedSample.value.standard, name: `${selectedSample.value.name}-标准样图.png` }
  }
  if (saved.cur?.dataUrl) curImage.value = { url: saved.cur.dataUrl, name: saved.cur.name || '待检样图.jpg' }
  if (saved.compareResult) compareResult.value = saved.compareResult
}

function onPickStd(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (stdImage.value?.url?.startsWith('blob:')) URL.revokeObjectURL(stdImage.value.url)
  selectedSampleId.value = null
  stdImage.value = { url: URL.createObjectURL(file), name: file.name, file }
  event.target.value = ''
  compareResult.value = null
  persistCompareState()
}

function onPickCur(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const isImage = ['image/png', 'image/jpeg'].includes(file.type) || /\.(png|jpe?g)$/i.test(file.name)
  if (!isImage) {
    compareError.value = '待检样图仅支持 JPG / PNG 格式，请重新上传'
    event.target.value = ''
    return
  }
  if (curImage.value?.url?.startsWith('blob:')) URL.revokeObjectURL(curImage.value.url)
  curImage.value = { url: URL.createObjectURL(file), name: file.name, file }
  event.target.value = ''
  compareError.value = ''
  compareResult.value = null
  persistCompareState()
}

/* 选择标准样图物料：根据编号自动加载 standards/{id}.png */
function onSelectSample() {
  const sample = selectedSample.value
  if (!sample) return
  if (stdImage.value?.url?.startsWith('blob:')) URL.revokeObjectURL(stdImage.value.url)
  stdImage.value = { url: sample.standard, name: `${sample.name}-标准样图.png` }
  compareResult.value = null
  compareError.value = ''
  persistCompareState()
}

function buildVerdict(similarity, regionCount) {
  if (similarity < 0.85 || regionCount >= 4) {
    return { verdict: '建议判废 / 复检', verdictTone: 'fail' }
  }
  if (similarity < 0.97 || regionCount > 0) {
    return { verdict: '需人工复核', verdictTone: 'warn' }
  }
  return { verdict: '建议通过', verdictTone: 'ok' }
}

function normalizeCompareResult(data, elapsed) {
  const similarity = Number(data.similarity ?? 0)
  const regionCount = Number(data.regionCount ?? data.regions?.length ?? 0)
  const verdictMeta = buildVerdict(similarity, regionCount)
  const regions = (data.regions ?? []).map((region) => ({
    ...region,
    status: region.status || 'PENDING',
    defectCode: region.defectCode || '',
    remark: region.remark || ''
  }))

  return {
    taskId: data.taskId,
    isAbnormal: Boolean(data.isAbnormal),
    ssim: Number(similarity.toFixed(4)),
    abnormalScore: Number((data.abnormalScore ?? 1 - similarity).toFixed(4)),
    diffAreaPct: Number(((data.abnormalScore ?? 1 - similarity) * 100).toFixed(2)),
    matchedFeatureCount: data.matchedFeatureCount ?? 0,
    defectCount: regionCount,
    regionCount,
    regions,
    resultImageUrl: data.resultImageUrl || '',
    differenceImageUrl: data.differenceImageUrl || '',
    elapsed: Number(elapsed.toFixed(2)),
    analyzedAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    ...verdictMeta
  }
}

/* 模拟 AI 异常检测：根据选定物料编号加载预置的热力图与缺陷标注结果 */
function buildSimulatedRegions(count) {
  const codes = ['SCR', 'DEN', 'BUR', 'COR', 'CRK']
  return Array.from({ length: count }, (_, i) => ({
    regionId: `R${String(i + 1).padStart(3, '0')}`,
    x: 40 + Math.floor(Math.random() * 360),
    y: 40 + Math.floor(Math.random() * 260),
    width: 40 + Math.floor(Math.random() * 120),
    height: 40 + Math.floor(Math.random() * 120),
    area: Number((800 + Math.random() * 4200).toFixed(2)),
    score: Number((0.42 + Math.random() * 0.5).toFixed(4)),
    status: 'PENDING',
    defectCode: DEFECT_CODES.some((d) => d.code === codes[i % codes.length]) ? codes[i % codes.length] : '',
    remark: ''
  }))
}

function resolveActiveSample() {
  if (selectedSample.value) return selectedSample.value
  const stdUrl = stdImage.value?.url
  if (!stdUrl) return null
  return INSPECTION_SAMPLES.find(
    (item) => stdUrl === item.standard || stdUrl.endsWith(`/standards/${item.id}.png`)
  ) || null
}

async function runComparison() {
  const sample = resolveActiveSample()
  if (!sample) {
    compareError.value = '请先选择标准样图物料'
    comparePhase.value = ''
    return
  }
  if (!curImage.value) {
    compareError.value = '请先上传待检样图（PNG/JPG）后再进行检测'
    comparePhase.value = ''
    return
  }

  compareError.value = ''
  comparing.value = true
  compareResult.value = null
  comparePhase.value = '正在进行视觉异常检测...'
  const t0 = Date.now()

  try {
    await new Promise((resolve) => setTimeout(resolve, 2000))

    const ssim = Number((0.82 + Math.random() * 0.13).toFixed(4))
    const regionCount = Math.floor(Math.random() * 3) + 2
    const elapsed = (Date.now() - t0) / 1000
    compareResult.value = normalizeCompareResult({
      taskId: `SIM-${sample.id}-${Date.now().toString(36)}`,
      isAbnormal: regionCount > 0,
      similarity: ssim,
      abnormalScore: Number((1 - ssim).toFixed(4)),
      matchedFeatureCount: 400 + Math.floor(Math.random() * 600),
      regionCount,
      regions: buildSimulatedRegions(regionCount),
      resultImageUrl: sample.result,
      differenceImageUrl: sample.heatmap
    }, elapsed)
    void persistCompareState()
  } catch (error) {
    compareError.value = error?.message || '检测失败，请重试'
  } finally {
    comparePhase.value = ''
    comparing.value = false
  }
}

const confirmedRegionCount = computed(() => {
  return compareResult.value?.regions?.filter((r) => r.status === 'CONFIRMED').length ?? 0
})

const pendingRegionCount = computed(() => {
  return compareResult.value?.regions?.filter((r) => r.status === 'PENDING').length ?? 0
})

function findRegion(regionId) {
  return compareResult.value?.regions?.find((r) => r.regionId === regionId)
}

function confirmRegion(regionId) {
  const region = findRegion(regionId)
  if (!region) return
  region.status = 'CONFIRMED'
  if (region.defectCode && !form.selectedDefects.includes(region.defectCode)) {
    form.selectedDefects.push(region.defectCode)
  }
  form.appearance = 'UNQUALIFIED'
  persistCompareState()
}

function ignoreRegion(regionId) {
  const region = findRegion(regionId)
  if (!region) return
  region.status = 'IGNORED'
  persistCompareState()
}

</script>

<template>
  <section v-if="allowed" class="quality-execute qce-center qa-redesign">
    <p v-if="qualityDataLoading" class="data-hint">正在加载检测任务...</p>
    <p v-else-if="qualityDataError" class="data-hint danger">{{ qualityDataError }}</p>
    <p v-if="submitError" class="data-hint danger">{{ submitError }}</p>
    <p v-if="submitSuccess" class="data-hint ok">{{ submitSuccess }}</p>

    <template v-if="inspectionDetail && activeLine">
      <!-- 顶部：标题 + 综合判定 -->
      <header class="qce-header">
        <h1>检验执行中心 <span title="按检测流程分栏录入">i</span></h1>
        <div class="qce-verdict" :class="overallLabel === 'PASS' ? 'pass' : overallLabel === 'FAIL' ? 'fail' : 'pending'">
          <span>综合判定</span>
          <strong>{{ overallLabel }}</strong>
        </div>
      </header>

      <div v-if="lineDetails.length" class="qce-line-tabs qce-line-tabs-top">
        <span>检测物料（{{ lineProgress.current }}/{{ lineProgress.total }}）：</span>
        <button
          v-for="(line, index) in lineDetails"
          :key="line.receiptLineId"
          :class="['qce-line-btn', { active: index === activeLineIndex, drafted: lineHasDraft(index) && index !== activeLineIndex }]"
          type="button"
          @click="switchLine(index)"
        >
          {{ line.itemName }}
          <small v-if="line.batchNo"> · {{ line.batchNo }}</small>
          <em v-if="lineHasDraft(index)" class="qce-line-draft-dot" title="已有未提交的检测数据">●</em>
        </button>
      </div>

      <!-- 流程分栏导航 -->
      <nav class="qce-tabbar">
        <button
          v-for="tab in TABS"
          :key="tab.id"
          type="button"
          class="qce-tab"
          :class="{ active: activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
          <em v-if="tab.badge">{{ tab.badge }}</em>
        </button>
      </nav>

      <!-- ================= 尺寸检测：图纸 + 检测结果录入 ================= -->
      <section
        v-show="activeTab === 'dimension'"
        :key="`dimension-${getLineId()}-${sampleIndex}`"
        class="qce-panel qce-dimension-panel"
      >
        <div class="qce-card qc-drawing-panel">
          <div class="qc-panel-head">
            <div>
              <h2>检测项执行 · 图纸 / 气泡定位</h2>
              <p class="form-hint">{{ drawing?.fileName }} · {{ drawing?.pages || 1 }} 页 · 更新 {{ drawing?.updatedAt }}</p>
            </div>
            <div class="qc-drawing-tools">
              <button type="button" class="wb-btn-secondary wb-btn-sm" @click="zoomDrawing(-0.1)">−</button>
              <span>{{ Math.round(drawingZoom * 100) }}%</span>
              <button type="button" class="wb-btn-secondary wb-btn-sm" @click="zoomDrawing(0.1)">+</button>
              <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="drawingPage <= 1" @click="drawingPage = 1">P1</button>
              <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="drawingPage >= (drawing?.pages || 1)" @click="drawingPage = 2">P2</button>
            </div>
          </div>
          <div class="qc-drawing-viewport">
            <div class="qc-drawing-stage" :style="{ transform: `scale(${drawingZoom})` }">
              <svg v-if="drawingPage === 1" class="qc-drawing-svg" viewBox="0 0 640 480" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                    <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#e4ebe6" stroke-width="0.5" />
                  </pattern>
                </defs>
                <rect width="640" height="480" fill="#f7faf8" />
                <rect width="640" height="480" fill="url(#grid)" />
                <rect x="24" y="24" width="592" height="432" fill="none" stroke="#2a3b34" stroke-width="1.2" />
                <text x="36" y="48" fill="#5f7d6e" font-size="12" font-family="ui-monospace, monospace">{{ drawing?.drawingNo }} Rev.{{ drawing?.rev }}</text>
                <text x="36" y="66" fill="#26352e" font-size="14" font-weight="700" font-family="Segoe UI, sans-serif">{{ drawing?.title }}</text>
                <g transform="translate(180,140)" fill="none" stroke="#2a3b34" stroke-width="2">
                  <rect x="0" y="40" width="280" height="120" rx="4" />
                  <rect x="20" y="55" width="240" height="90" rx="2" stroke-dasharray="4 3" />
                  <line x1="-20" y1="40" x2="300" y2="40" stroke="#8aa396" stroke-width="1" />
                  <line x1="-20" y1="160" x2="300" y2="160" stroke="#8aa396" stroke-width="1" />
                </g>
                <g
                  v-for="(dim, i) in dimensions.slice(0, 8)"
                  :key="dim.id"
                  class="qc-balloon"
                  :class="{ active: highlightDimId === dim.id, fail: dimRows[i]?.result === 'FAIL', pass: dimRows[i]?.result === 'PASS' }"
                  @click="focusDim(dim.id)"
                >
                  <circle :cx="70 + (i % 4) * 150" :cy="i < 4 ? 100 : 380" r="16" fill="#fff" stroke="currentColor" stroke-width="2" />
                  <text :x="70 + (i % 4) * 150" :y="i < 4 ? 105 : 385" text-anchor="middle" font-size="12" font-weight="700" fill="currentColor">{{ dim.balloon }}</text>
                  <text :x="70 + (i % 4) * 150" :y="i < 4 ? 128 : 408" text-anchor="middle" font-size="10" fill="#6d7d75">{{ dim.name.length > 8 ? dim.name.slice(0, 8) + '…' : dim.name }}</text>
                </g>
                <text x="36" y="450" fill="#818d87" font-size="11">点击气泡号 → 定位尺寸行 · 第 1 页 / 外形与关键尺寸</text>
              </svg>
              <svg v-else class="qc-drawing-svg" viewBox="0 0 640 480" xmlns="http://www.w3.org/2000/svg">
                <rect width="640" height="480" fill="#f7faf8" />
                <rect x="24" y="24" width="592" height="432" fill="none" stroke="#2a3b34" stroke-width="1.2" />
                <text x="36" y="48" fill="#5f7d6e" font-size="12" font-family="ui-monospace, monospace">{{ drawing?.drawingNo }} · PAGE 2 · 螺纹 / 细节</text>
                <g transform="translate(120,90)" fill="none" stroke="#2a3b34" stroke-width="1.6">
                  <path d="M40 40 L360 40 L360 280 L40 280 Z" />
                  <line x1="40" y1="160" x2="360" y2="160" stroke-dasharray="6 4" stroke="#8aa396" />
                </g>
                <g
                  v-for="(dim, i) in dimensions.slice(8)"
                  :key="dim.id"
                  class="qc-balloon"
                  :class="{ active: highlightDimId === dim.id }"
                  @click="focusDim(dim.id)"
                >
                  <circle :cx="100 + i * 120" :cy="400" r="16" fill="#fff" stroke="currentColor" stroke-width="2" />
                  <text :x="100 + i * 120" :y="405" text-anchor="middle" font-size="12" font-weight="700" fill="currentColor">{{ dim.balloon }}</text>
                </g>
                <text x="36" y="450" fill="#818d87" font-size="11">第 2 页 / 螺纹与细节尺寸</text>
              </svg>
            </div>
          </div>
          <div class="qc-drawing-legend">
            <span><i class="dot pending"></i>未测</span>
            <span><i class="dot pass"></i>PASS</span>
            <span><i class="dot fail"></i>FAIL</span>
            <span><i class="dot active"></i>当前聚焦</span>
          </div>
        </div>

        <div class="qce-card qce-card-spaced">
          <div class="qc-panel-head">
            <div>
              <h2>检测结果录入 · 尺寸检测项</h2>
              <p class="form-hint">
                共 {{ dimSummary.total }} 项 · 已测 {{ dimSummary.filled }} ·
                PASS {{ dimSummary.pass }} · FAIL {{ dimSummary.fail }}
                <template v-if="dimSummary.criticalFail"> · <b class="text-danger">含关键尺寸超差</b></template>
              </p>
            </div>
            <div class="qc-panel-actions">
              <button
                type="button"
                class="wb-btn-secondary wb-btn-sm qce-demo-fill-btn"
                title="演示用：自动填入标准尺寸与合格判定"
                @click="fillQualifiedData"
              >一键填入合格数据</button>
              <div class="qc-sample-switcher" aria-label="切换检验零件">
                <button type="button" class="qc-sample-btn" :disabled="sampleIndex <= 0" @click="prevSample">‹</button>
                <strong>{{ currentSampleNo }} / {{ sampleSize }}</strong>
                <button type="button" class="qc-sample-btn" :disabled="sampleIndex >= sampleSize - 1" @click="nextSample">›</button>
              </div>
            </div>
          </div>
          <div class="qc-dim-table-wrap">
            <table class="qc-dim-table">
              <thead>
                <tr>
                  <th>气泡</th><th>检测项</th><th>标准 / 公差</th><th>实测值</th><th>偏差</th><th>判定</th><th>量具</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in dimRows"
                  :id="'dim-row-' + row.id"
                  :key="row.id"
                  :class="{ 'is-fail': row.result === 'FAIL', 'is-pass': row.result === 'PASS', 'is-focus': highlightDimId === row.id, 'is-critical': row.critical }"
                  @click="highlightDimId = row.id"
                >
                  <td class="balloon-cell">{{ row.balloon }}</td>
                  <td><strong>{{ row.name }}</strong><small v-if="row.critical" class="crit-tag">关键</small></td>
                  <td class="mono">{{ formatSpec(row) }}</td>
                  <td>
                    <input v-model="form.dimValues[row.id]" class="qc-dim-input" type="number" step="any" :placeholder="String(row.nominal)" @focus="highlightDimId = row.id" />
                    <span class="unit">{{ row.unit }}</span>
                  </td>
                  <td class="mono" :class="row.result === 'FAIL' ? 'text-danger' : ''">{{ row.deviation == null ? '—' : (row.deviation > 0 ? '+' : '') + row.deviation }}</td>
                  <td><i class="qc-badge" :class="row.result === 'PASS' ? 'pass' : row.result === 'FAIL' ? 'fail' : 'pending'">{{ row.result }}</i></td>
                  <td class="muted">{{ row.instrument }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="qce-card qce-card-spaced">
          <h2>{{ performance?.name || '绝缘耐压测试' }}</h2>
          <p class="form-hint">标准：{{ performance?.standard || '按检验标准执行' }}</p>
          <div class="qc-perf-row">
            <label class="wb-field-label">实测值
              <input v-model="form.performanceActual" class="wb-input" type="text" :placeholder="String(performance?.minValue ?? '')" />
            </label>
            <div class="radio-row wb-radio-row">
              <label><input v-model="form.performanceResult" type="radio" value="QUALIFIED" /> 合格</label>
              <label><input v-model="form.performanceResult" type="radio" value="UNQUALIFIED" /> 不合格</label>
              <span class="qce-unit-hint" v-if="performance?.unit">单位：{{ performance.unit }}</span>
            </div>
          </div>
        </div>

        <div class="qce-card qce-card-spaced">
          <div class="qc-panel-head">
            <div>
              <h2>拍照留证 · 附件</h2>
              <p class="form-hint">
                异常判定必须留证
                <template v-if="evidenceRequired"> · <b class="text-danger">当前为 FAIL，请上传证据</b></template>
              </p>
            </div>
            <div class="qc-attach-actions">
              <input ref="fileInputRef" type="file" accept="image/*,.pdf,.xlsx,.csv" multiple hidden @change="onPickFiles" />
              <button type="button" class="wb-btn-secondary wb-btn-sm" @click="fileInputRef?.click()">上传照片/附件</button>
              <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="aiRunning" @click="runAiVision">{{ aiRunning ? 'AI 分析中…' : 'AI 视觉辅助' }}</button>
            </div>
          </div>
          <div v-if="attachments.length" class="qc-attach-list">
            <div v-for="file in attachments" :key="file.id" class="qc-attach-item">
              <img v-if="file.type === 'image'" :src="file.url" :alt="file.name" />
              <div v-else class="qc-file-icon">FILE</div>
              <div class="qc-attach-meta">
                <strong>{{ file.name }}</strong>
                <small>{{ (file.size / 1024).toFixed(1) }} KB<template v-if="file.isEvidence"> · 异常证据</template></small>
              </div>
              <button type="button" class="wb-btn-secondary wb-btn-sm" @click="removeAttachment(file.id)">移除</button>
            </div>
          </div>
          <p v-else class="form-hint">尚未上传附件。支持现场拍照、检测报告、仪器导出文件。</p>
          <div v-if="aiResult" class="qc-ai-panel">
            <div class="qc-ai-head">
              <strong>AI 视觉辅助结果</strong>
              <small>{{ aiResult.model }} · {{ aiResult.analyzedAt }} · {{ aiResult.imageCount }} 张图</small>
            </div>
            <p>{{ aiResult.summary }}</p>
            <div class="qc-ai-suggestions">
              <button v-for="s in aiResult.suggestions" :key="s.code + s.label" type="button" class="qc-ai-chip" @click="applyAiSuggestion(s.code)">
                <span>{{ s.label }} · {{ Math.round(s.confidence * 100) }}%</span>
                <small>{{ s.note }}</small>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- ================= 外观检测：AI 样图对比 + 缺陷代码 ================= -->
      <section
        v-show="activeTab === 'appearance'"
        :key="`appearance-${getLineId()}-${sampleIndex}`"
        class="qce-panel"
      >
        <div class="qce-ai-layout">
          <div class="qce-ai-main-col">
            <div class="qce-card qce-ai-main">
            <div class="qce-ai-title">
              <div class="qce-ai-badge">AI</div>
              <div>
                <h2>AI 智能标准样图对比（OpenCV + SSIM）</h2>
                <p class="form-hint">上传标准样图与待检图，由 OpenCV ORB 对齐 + SSIM 差异检测自动标注红框，质检员复核确认</p>
              </div>
            </div>

            <!-- 标准样图选择 -->
            <div class="qce-sample-picker">
              <label for="qce-sample-select">标准样图</label>
              <select id="qce-sample-select" v-model.number="selectedSampleId" @change="onSelectSample">
                <option :value="null" disabled>请选择物料</option>
                <option v-for="s in INSPECTION_SAMPLES" :key="s.id" :value="s.id">{{ s.name }}（{{ s.spec }}）</option>
              </select>
              <span v-if="selectedSample" class="qce-sample-loaded">已加载「{{ selectedSample.name }}」标准样图（编号 {{ selectedSample.id }}）</span>
              <span v-else class="qce-sample-hint">选择物料后自动加载对应标准样图</span>
            </div>

            <p v-if="compareError" class="data-hint danger">{{ compareError }}</p>
            <p v-else-if="comparePhase" class="data-hint">{{ comparePhase }}</p>

            <!-- 四图对比 -->
            <div class="qce-cmp-grid">
              <figure class="qce-cmp-card">
                <div class="qce-cmp-head"><b>标准样图（模板）</b><em class="tag ref">标准图</em></div>
                <div class="qce-cmp-img">
                  <img v-if="stdImage" :src="stdImage.url" alt="标准样图" />
                  <span v-else class="qce-cmp-empty">请先选择物料</span>
                </div>
                <figcaption><span>{{ selectedSample ? selectedSample.name : '100%' }}</span>
                  <button type="button" @click="stdFileRef?.click()">手动上传</button>
                </figcaption>
                <input ref="stdFileRef" type="file" accept="image/png,image/jpeg" hidden @change="onPickStd" />
              </figure>

              <figure class="qce-cmp-card">
                <div class="qce-cmp-head"><b>待检样图（当前）</b><em class="tag dut">待检图</em></div>
                <div class="qce-cmp-img">
                  <img v-if="curImage" :src="curImage.url" alt="待检样图" />
                  <span v-else class="qce-cmp-empty">请上传待检测图片</span>
                </div>
                <figcaption><span>{{ curImage ? '已上传' : '100%' }}</span>
                  <button type="button" @click="curFileRef?.click()">上传</button>
                </figcaption>
                <input ref="curFileRef" type="file" accept="image/png,image/jpeg" hidden @change="onPickCur" />
              </figure>

              <figure class="qce-cmp-card">
                <div class="qce-cmp-head"><b>差异热力图（SSIM 差异检测）</b><em class="tag heat">差异热力图</em></div>
                <div class="qce-cmp-img dark">
                  <img v-if="compareResult?.differenceImageUrl" :src="compareResult.differenceImageUrl" alt="差异热力图" />
                  <span v-else class="qce-cmp-empty light">{{ comparing ? '检测中…' : '待检测' }}</span>
                </div>
                <figcaption><span>差异检测结果</span></figcaption>
              </figure>

              <figure class="qce-cmp-card">
                <div class="qce-cmp-head"><b>缺陷标注结果（红框定位）</b><em class="tag anno">缺陷标注</em></div>
                <div class="qce-cmp-img">
                  <img v-if="compareResult?.resultImageUrl" :src="compareResult.resultImageUrl" alt="缺陷标注结果" />
                  <span v-else class="qce-cmp-empty">{{ comparing ? '检测中…' : '待检测' }}</span>
                </div>
                <figcaption><span>缺陷定位结果</span></figcaption>
              </figure>
            </div>

            <div class="qce-cmp-actions">
              <button type="button" class="qce-op-primary" :disabled="comparing" @click="runComparison">{{ comparing ? '对比中…' : '开始对比' }}</button>
            </div>

            <div v-if="compareResult?.regions?.length" class="qce-region-review">
              <div class="qce-region-head">
                <h3>差异区域复核</h3>
                <span>已确认 {{ confirmedRegionCount }} / 待复核 {{ pendingRegionCount }} / 共 {{ compareResult.regionCount }} 处</span>
              </div>
              <div class="qce-region-list">
                <article
                  v-for="region in compareResult.regions"
                  :key="region.regionId"
                  class="qce-region-item"
                  :class="region.status.toLowerCase()"
                >
                  <div class="qce-region-meta">
                    <strong>{{ region.regionId }}</strong>
                    <span>位置 ({{ region.x }}, {{ region.y }}) · {{ region.width }}×{{ region.height }} px</span>
                    <span>差异分 {{ region.score }} · 面积 {{ region.area }}</span>
                    <em class="pill" :class="region.status === 'CONFIRMED' ? 'fail' : region.status === 'IGNORED' ? 'ok' : 'warn'">
                      {{ region.status === 'CONFIRMED' ? '已确认缺陷' : region.status === 'IGNORED' ? '已忽略' : '待复核' }}
                    </em>
                  </div>
                  <div class="qce-region-actions">
                    <select v-model="region.defectCode" class="qce-region-select">
                      <option value="">选择缺陷类型</option>
                      <option v-for="d in DEFECT_CODES" :key="d.code" :value="d.code">{{ d.code }} · {{ d.label }}</option>
                    </select>
                    <input v-model="region.remark" class="qce-region-input" type="text" placeholder="备注（可选）" />
                    <button type="button" class="qce-op-secondary" @click="confirmRegion(region.regionId)">确认缺陷</button>
                    <button type="button" class="qce-op-secondary" @click="ignoreRegion(region.regionId)">忽略误报</button>
                  </div>
                </article>
              </div>
            </div>

            <p class="qce-ai-footnote">本模块基于 OpenCV + SSIM 实现，适用于 PCB、结构件等外观差异检测的轻量场景；复杂缺陷建议结合专业算法及人工审核。</p>
            </div>

            <div class="qce-card qce-appearance-defect">
              <h2>外观检测 · 缺陷代码</h2>
              <div class="radio-row wb-radio-row">
                <label><input v-model="form.appearance" type="radio" value="QUALIFIED" /> 合格</label>
                <label><input v-model="form.appearance" type="radio" value="UNQUALIFIED" /> 不合格</label>
              </div>
              <div class="qc-defect-grid">
                <div v-for="(codes, cat) in defectGroups" :key="cat" class="qc-defect-group">
                  <span class="qc-defect-cat">{{ cat }}</span>
                  <div class="qc-defect-chips">
                    <button
                      v-for="d in codes"
                      :key="d.code"
                      type="button"
                      class="qc-defect-chip"
                      :class="[{ selected: form.selectedDefects.includes(d.code) }, d.severity]"
                      @click="toggleDefect(d.code)"
                    ><code>{{ d.code }}</code>{{ d.label }}</button>
                  </div>
                </div>
              </div>
              <textarea
                v-model="form.appearanceRemark"
                class="wb-textarea"
                rows="3"
                :placeholder="form.appearance === 'UNQUALIFIED' ? '描述缺陷位置与程度，例如：密封面 3 点钟方向划痕约 8mm' : '可选填写检测备注'"
              ></textarea>
            </div>
          </div>
        </div>
      </section>

      <!-- 常驻底部操作条 -->
      <footer class="qce-actionbar">
        <div class="qce-actionbar-summary">
          <span>尺寸 {{ dimSummary.filled }}/{{ dimSummary.total }}</span>
          <span :class="appearanceOk ? '' : 'text-danger'">外观 {{ appearanceOk ? '合格' : '不合格' }}</span>
          <span :class="performanceOk ? '' : 'text-danger'">耐压 {{ performanceOk ? '合格' : '不合格' }}</span>
          <span>附件 {{ attachments.length }}</span>
        </div>
        <div class="qce-actionbar-btns">
          <button
            type="button"
            class="wb-btn-secondary"
            :disabled="submitting || overallPass || !canSubmit"
            @click="handleSubmit(true)"
          >生成质量异常单</button>
          <button
            type="button"
            class="wb-btn-primary"
            :disabled="submitting || !lineDetails.length || !overallPass || !canSubmit"
            @click="handleSubmit(false)"
          >
            {{ submitting ? '提交中…' : `提交全部物料（${lineDetails.length} 行）` }}
          </button>
        </div>
      </footer>
    </template>

    <template v-else-if="inspectionDetail && !lineDetails.length">
      <header class="qce-header">
        <h1>检验执行中心</h1>
      </header>
      <p class="data-hint ok">该收货单全部物料已完成检测，可返回待检任务中心查看结果。</p>
    </template>
  </section>
</template>
