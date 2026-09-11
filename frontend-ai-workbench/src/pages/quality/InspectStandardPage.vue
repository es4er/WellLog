<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoleAccess } from '../../composables/useRoleAccess'
import { apiGet, apiPost } from '../../api'
import { downloadCsv } from '../../utils/tableExport'

const router = useRouter()
const { allowed } = useRoleAccess('quality')

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const keyword = ref('')
const statusFilter = ref('')
const categoryFilter = ref('item')
const standards = ref([])
const inspectItems = ref([])
const mdItems = ref([])
const selectedId = ref(null)
const detailLoaded = ref(false)
const basicEditable = ref(false)
const editingLineIdx = ref(-1)
const linesSectionOpen = ref(false)
const expandedLineSet = ref(new Set())
const page = ref(1)
const pageSize = 4

/** 测井工具 10 种物料（与检验执行/样图物料一致） */
const LOGGING_ITEM_CODES = [
  'M-LOG-FLANGE',
  'M-LOG-PROBE-SHELL',
  'M-LOG-PRESS-CYL',
  'M-LOG-CONN-JOINT',
  'M-LOG-THREAD-JOINT',
  'M-LOG-CENTRALIZER',
  'M-LOG-STAB-BLADE',
  'M-LOG-SLIP-SEAT',
  'M-LOG-TOP-SUB',
  'M-LOG-BOP-SUB'
]

const LOGGING_ITEM_LABELS = {
  'M-LOG-FLANGE': '法兰盘',
  'M-LOG-PROBE-SHELL': '探头外壳',
  'M-LOG-PRESS-CYL': '压力筒',
  'M-LOG-CONN-JOINT': '连接接头',
  'M-LOG-THREAD-JOINT': '螺纹接头',
  'M-LOG-CENTRALIZER': '中心定位器',
  'M-LOG-STAB-BLADE': '稳定器叶片',
  'M-LOG-SLIP-SEAT': '卡瓦座',
  'M-LOG-TOP-SUB': '上接头',
  'M-LOG-BOP-SUB': '防喷接头'
}

function loggingItemOrder(code) {
  const idx = LOGGING_ITEM_CODES.indexOf(code || '')
  return idx >= 0 ? idx : 999
}

function sortStandards(list) {
  return [...list].sort((a, b) => {
    const orderA = a.mdItemCode ? loggingItemOrder(a.mdItemCode) : 1000
    const orderB = b.mdItemCode ? loggingItemOrder(b.mdItemCode) : 1000
    if (orderA !== orderB) return orderA - orderB
    if (!a.mdItemId && b.mdItemId) return 1
    if (a.mdItemId && !b.mdItemId) return -1
    return String(a.standardCode || '').localeCompare(String(b.standardCode || ''))
  })
}

const loggingMdItems = computed(() =>
  LOGGING_ITEM_CODES
    .map((code) => mdItems.value.find((item) => item.itemCode === code))
    .filter(Boolean)
)

const form = reactive({
  standardId: null,
  standardCode: '',
  standardName: '',
  mdItemId: null,
  versionNo: 'A',
  aqlLevel: 'II',
  aqlValue: 1.5,
  drawingNo: '',
  status: 'ENABLED',
  remark: '',
  category: '来料检验标准',
  lines: []
})

const enabledInspectItems = computed(() => inspectItems.value.filter((i) => i.status === 'ENABLED'))

const filteredStandards = computed(() => {
  return standards.value.filter((row) => {
    if (categoryFilter.value === 'general' && row.mdItemId) return false
    if (categoryFilter.value === 'item' && !row.mdItemId) return false
    return true
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredStandards.value.length / pageSize)))
const pagedStandards = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredStandards.value.slice(start, start + pageSize)
})

const selectedMaterialLabel = computed(() => {
  if (!form.mdItemId) return '通用（不绑定物料）'
  const item = mdItems.value.find((i) => i.itemId === form.mdItemId)
  return item ? `${item.itemCode} / ${item.itemName}` : `物料#${form.mdItemId}`
})

onMounted(async () => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('检验标准'))
    return
  }
  await Promise.all([loadStandards(), loadInspectItems(), loadMdItems()])
})

watch(selectedId, async (id) => {
  if (!id) return
  await openDetail(id)
})

watch([keyword, statusFilter, categoryFilter], () => {
  page.value = 1
})

function qs(params) {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  })
  const s = q.toString()
  return s ? `?${s}` : ''
}

async function loadStandards() {
  loading.value = true
  error.value = ''
  try {
    standards.value = sortStandards(await apiGet(`/quality/standards${qs({
      keyword: keyword.value,
      status: statusFilter.value
    })}`))
    if (!selectedId.value && standards.value.length) {
      selectedId.value = standards.value[0].standardId
    } else if (selectedId.value && !standards.value.some((s) => s.standardId === selectedId.value)) {
      selectedId.value = standards.value[0]?.standardId ?? null
      if (!selectedId.value) detailLoaded.value = false
    }
  } catch (e) {
    error.value = e.message || '加载检验标准失败'
  } finally {
    loading.value = false
  }
}

async function loadInspectItems() {
  inspectItems.value = await apiGet('/quality/inspect-items?status=ENABLED')
}

async function loadMdItems() {
  mdItems.value = await apiGet('/quality/master/items')
}

function resetLineUi() {
  linesSectionOpen.value = false
  expandedLineSet.value = new Set()
  editingLineIdx.value = -1
}

function isLineExpanded(idx) {
  return expandedLineSet.value.has(idx) || editingLineIdx.value === idx
}

function toggleLineExpand(idx) {
  if (editingLineIdx.value === idx) return
  const next = new Set(expandedLineSet.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  expandedLineSet.value = next
}

function expandLine(idx) {
  const next = new Set(expandedLineSet.value)
  next.add(idx)
  expandedLineSet.value = next
  linesSectionOpen.value = true
}

function startEditLine(idx) {
  expandLine(idx)
  editingLineIdx.value = idx
}

function finishEditLine() {
  editingLineIdx.value = -1
}

async function openDetail(id) {
  try {
    const detail = await apiGet(`/quality/standards/${id}`)
    applyDetail(detail)
    detailLoaded.value = true
    basicEditable.value = false
    resetLineUi()
  } catch (e) {
    error.value = e.message || '加载标准详情失败'
  }
}

function applyDetail(detail) {
  form.standardId = detail.standardId
  form.standardCode = detail.standardCode
  form.standardName = detail.standardName
  form.mdItemId = detail.mdItemId
  form.versionNo = detail.versionNo || 'A'
  form.aqlLevel = detail.aqlLevel || 'II'
  form.aqlValue = detail.aqlValue ?? 1.5
  form.drawingNo = detail.drawingNo || ''
  form.status = detail.status || 'ENABLED'
  form.remark = detail.remark || ''
  form.category = detail.mdItemId ? '物料专属标准' : '来料检验标准'
  form.lines = (detail.lines || []).map((line) => ({
    inspectItemId: line.inspectItemId,
    itemCode: line.itemCode,
    itemName: line.itemName,
    itemType: line.itemType,
    requiredFlag: line.requiredFlag ?? 1,
    standardText: line.standardText || '',
    nominal: line.nominal,
    lowerTol: line.lowerTol,
    upperTol: line.upperTol,
    minValue: line.minValue,
    maxValue: line.maxValue,
    unit: line.unit || '',
    criticalFlag: line.criticalFlag ?? 0,
    sortNo: line.sortNo ?? 0
  }))
}

function openCreate() {
  form.standardId = null
  form.standardCode = ''
  form.standardName = ''
  form.mdItemId = null
  form.versionNo = 'A'
  form.aqlLevel = 'II'
  form.aqlValue = 1.5
  form.drawingNo = ''
  form.status = 'ENABLED'
  form.remark = ''
  form.category = '来料检验标准'
  form.lines = []
  selectedId.value = null
  detailLoaded.value = true
  basicEditable.value = true
  resetLineUi()
  message.value = ''
}

function selectStandard(row) {
  selectedId.value = row.standardId
}

function addLine() {
  const first = enabledInspectItems.value.find(
    (item) => !form.lines.some((l) => l.inspectItemId === item.inspectItemId)
  )
  if (!first) {
    error.value = '没有可添加的检验项，请先在检验标准中选用已有检验项库数据'
    return
  }
  form.lines.push({
    inspectItemId: first.inspectItemId,
    itemCode: first.itemCode,
    itemName: first.itemName,
    itemType: first.itemType,
    requiredFlag: 1,
    standardText: first.defaultStandard || '',
    nominal: first.itemType === 'DIMENSION' ? 10 : null,
    lowerTol: first.itemType === 'DIMENSION' ? -0.05 : null,
    upperTol: first.itemType === 'DIMENSION' ? 0.05 : null,
    minValue: null,
    maxValue: null,
    unit: first.unit || '',
    criticalFlag: first.criticalFlag ? 1 : 0,
    sortNo: (form.lines.length + 1) * 10
  })
  const newIdx = form.lines.length - 1
  linesSectionOpen.value = true
  startEditLine(newIdx)
}

function onLineItemChange(line) {
  const item = enabledInspectItems.value.find((i) => i.inspectItemId === line.inspectItemId)
  if (!item) return
  line.itemCode = item.itemCode
  line.itemName = item.itemName
  line.itemType = item.itemType
  if (!line.standardText) line.standardText = item.defaultStandard || ''
  if (!line.unit) line.unit = item.unit || ''
  line.criticalFlag = item.criticalFlag ? 1 : 0
}

function removeLine(idx) {
  form.lines.splice(idx, 1)
  const next = new Set()
  expandedLineSet.value.forEach((i) => {
    if (i < idx) next.add(i)
    else if (i > idx) next.add(i - 1)
  })
  expandedLineSet.value = next
  if (editingLineIdx.value === idx) editingLineIdx.value = -1
  else if (editingLineIdx.value > idx) editingLineIdx.value -= 1
}

async function saveStandard() {
  if (!form.standardCode.trim() || !form.standardName.trim()) {
    error.value = '请填写标准编码和名称'
    return
  }
  if (!form.lines.length) {
    error.value = '请至少添加一条检验项'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const saved = await apiPost('/quality/standards/save', {
      standard: {
        standardId: form.standardId,
        standardCode: form.standardCode,
        standardName: form.standardName,
        mdItemId: form.mdItemId || null,
        versionNo: form.versionNo,
        aqlLevel: form.aqlLevel,
        aqlValue: form.aqlValue,
        drawingNo: form.drawingNo,
        status: form.status,
        remark: form.remark
      },
      lines: form.lines.map((line, idx) => ({
        inspectItemId: line.inspectItemId,
        requiredFlag: line.requiredFlag ? 1 : 0,
        standardText: line.standardText,
        nominal: line.nominal,
        lowerTol: line.lowerTol,
        upperTol: line.upperTol,
        minValue: line.minValue,
        maxValue: line.maxValue,
        unit: line.unit,
        criticalFlag: line.criticalFlag ? 1 : 0,
        sortNo: line.sortNo ?? (idx + 1) * 10
      }))
    })
    message.value = '检验标准已保存'
    basicEditable.value = false
    resetLineUi()
    await loadStandards()
    selectedId.value = saved.standardId
    applyDetail(saved)
    detailLoaded.value = true
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function publishStandard() {
  form.status = 'ENABLED'
  await saveStandard()
  if (!error.value) message.value = '标准已发布（启用）'
}

async function disableCurrent() {
  if (!form.standardId) {
    error.value = '请先保存标准后再停用'
    return
  }
  if (!confirm(`确认停用标准「${form.standardName}」？`)) return
  try {
    await apiPost(`/quality/standards/${form.standardId}/disable`)
    message.value = '已停用'
    form.status = 'DISABLED'
    await loadStandards()
  } catch (e) {
    error.value = e.message || '停用失败'
  }
}

function exportStandard() {
  if (!form.standardCode) {
    error.value = '暂无可导出的标准'
    return
  }
  downloadCsv(
    `${form.standardCode}-检验标准.csv`,
    ['标准编码', '标准名称', '版本', '物料', '检验项编码', '检验项名称', '类型', '判定说明', '必检', '公差/限值'],
    form.lines.map((line) => [
      form.standardCode,
      form.standardName,
      form.versionNo,
      selectedMaterialLabel.value,
      line.itemCode,
      line.itemName,
      typeLabel(line.itemType),
      line.standardText,
      line.requiredFlag ? '必检' : '选检',
      formatTolerance(line)
    ])
  )
  message.value = '已导出当前标准'
}

function typeLabel(type) {
  return ({ APPEARANCE: '外观', DIMENSION: '尺寸', PERFORMANCE: '性能', OTHER: '其他' })[type] || type || '—'
}

function methodLabel(type) {
  return ({
    APPEARANCE: '目视检查',
    DIMENSION: '卡尺/量具测量',
    PERFORMANCE: '功能/性能测试',
    OTHER: '按作业指导'
  })[type] || '按作业指导'
}

function formatTolerance(line) {
  if (line.lowerTol != null || line.upperTol != null) {
    const lo = line.lowerTol ?? ''
    const hi = line.upperTol ?? ''
    const unit = line.unit || 'mm'
    if (line.nominal != null) return `${line.nominal} (${lo}/${hi > 0 ? '+' : ''}${hi})${unit}`
    return `${lo}/${hi}${unit}`
  }
  if (line.minValue != null || line.maxValue != null) {
    const unit = line.unit || ''
    if (line.minValue != null && line.maxValue != null) return `${line.minValue} ~ ${line.maxValue}${unit}`
    if (line.minValue != null) return `≥ ${line.minValue}${unit}`
    return `≤ ${line.maxValue}${unit}`
  }
  return '—'
}

function materialText(row) {
  if (!row.mdItemId) return '关联物料：通用（不绑定物料）'
  const label = LOGGING_ITEM_LABELS[row.mdItemCode] || row.mdItemName || ''
  return `关联物料：${row.mdItemCode || ''} ${label}`.trim()
}

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign std-mgmt">
    <p v-if="loading" class="data-hint">正在加载检验标准...</p>
    <p v-else-if="error" class="data-hint danger">{{ error }}</p>
    <p v-if="message" class="data-hint">{{ message }}</p>

    <header class="qa-issue-header">
      <div>
        <h1>检验标准中心 <span>i</span></h1>
      </div>
    </header>

    <div class="std-main">
      <aside class="std-list-panel">
        <div class="std-list-head">
          <h2>检验标准列表</h2>
          <button type="button" class="std-btn primary" @click="openCreate">+ 新建标准</button>
        </div>

        <div class="std-list-filters">
          <input
            v-model="keyword"
            type="search"
            placeholder="请输入标准名称/编码"
            @keyup.enter="loadStandards"
          />
          <div class="std-filter-row">
            <select v-model="statusFilter" @change="loadStandards">
              <option value="">全部状态</option>
              <option value="ENABLED">启用</option>
              <option value="DISABLED">停用</option>
            </select>
            <select v-model="categoryFilter">
              <option value="">全部类别</option>
              <option value="general">通用标准</option>
              <option value="item">物料专属</option>
            </select>
          </div>
        </div>

        <div class="std-card-list">
          <button
            v-for="row in pagedStandards"
            :key="row.standardId"
            type="button"
            :class="['std-std-card', { active: row.standardId === selectedId }]"
            @click="selectStandard(row)"
          >
            <div class="std-std-card-top">
              <strong>{{ row.standardCode }}</strong>
              <span :class="['std-status', row.status === 'ENABLED' ? 'on' : 'off']">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </span>
            </div>
            <p class="std-std-name">{{ row.standardName }}</p>
            <p class="std-std-meta">{{ materialText(row) }}</p>
            <span class="std-item-chip">检验项：{{ row.lineCount ?? 0 }}</span>
          </button>
          <p v-if="!loading && !pagedStandards.length" class="std-empty">暂无匹配标准</p>
        </div>

        <div class="std-pager">
          <button type="button" class="std-btn ghost" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button type="button" class="std-btn ghost" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
        </div>
      </aside>

      <template v-if="detailLoaded">
        <div class="std-basic-panel">
          <div class="std-basic-only-label">基本信息</div>
          <div class="std-section std-basic-section">
            <div class="std-section-head">
              <h3>标准基本信息</h3>
              <button type="button" class="std-btn ghost" @click="basicEditable = !basicEditable">
                {{ basicEditable ? '完成编辑' : '编辑' }}
              </button>
            </div>

            <div class="std-form-grid" :class="{ readonly: !basicEditable }">
              <label>
                <span>标准编码</span>
                <input v-model="form.standardCode" :disabled="!!form.standardId || !basicEditable" />
              </label>
              <label>
                <span>标准名称</span>
                <input v-model="form.standardName" :disabled="!basicEditable" />
              </label>
              <label>
                <span>关联物料</span>
                <select v-model="form.mdItemId" :disabled="!basicEditable">
                  <option :value="null">通用（不绑定物料）</option>
                  <option v-for="item in loggingMdItems" :key="item.itemId" :value="item.itemId">
                    {{ LOGGING_ITEM_LABELS[item.itemCode] || item.itemName }}（{{ item.itemCode }}）
                  </option>
                </select>
              </label>
              <label>
                <span>版本</span>
                <input v-model="form.versionNo" :disabled="!basicEditable" />
              </label>
              <label>
                <span>AQL 水平</span>
                <select v-model="form.aqlLevel" :disabled="!basicEditable">
                  <option value="I">I</option>
                  <option value="II">II</option>
                  <option value="III">III</option>
                  <option value="S4">S-4</option>
                </select>
              </label>
              <label>
                <span>AQL 值</span>
                <input v-model.number="form.aqlValue" type="number" step="0.01" :disabled="!basicEditable" />
              </label>
              <label>
                <span>图纸号</span>
                <input v-model="form.drawingNo" :disabled="!basicEditable" placeholder="—" />
              </label>
              <label>
                <span>状态</span>
                <select v-model="form.status" :disabled="!basicEditable">
                  <option value="ENABLED">启用</option>
                  <option value="DISABLED">停用</option>
                </select>
              </label>
              <label class="span-2">
                <span>备注</span>
                <input v-model="form.remark" :disabled="!basicEditable" />
              </label>
              <label class="span-2">
                <span>标准类别</span>
                <input v-model="form.category" :disabled="!basicEditable" />
              </label>
            </div>
          </div>
        </div>

        <section class="std-detail-lower">
          <div class="std-detail-scroll">
            <div class="std-section std-lines-section">
              <div class="std-section-head std-section-head-toggle">
                <button type="button" class="std-section-toggle" @click="linesSectionOpen = !linesSectionOpen">
                  <span class="std-chevron" :class="{ open: linesSectionOpen }">›</span>
                  <h3>检验项目 <small>（共 {{ form.lines.length }} 项）</small></h3>
                </button>
                <button type="button" class="std-btn primary soft" @click="linesSectionOpen = true; addLine()">+ 添加检验项目</button>
              </div>

              <div v-show="linesSectionOpen" class="std-item-cards std-item-accordion">
                <article
                  v-for="(line, idx) in form.lines"
                  :key="`${line.inspectItemId}-${idx}`"
                  :class="['std-item-card', { editing: editingLineIdx === idx, expanded: isLineExpanded(idx) }]"
                >
                  <button type="button" class="std-item-card-toggle" @click="toggleLineExpand(idx)">
                    <span class="std-chevron" :class="{ open: isLineExpanded(idx) }">›</span>
                    <span class="std-item-toggle-main">
                      <strong>{{ line.itemName || '未命名检验项' }}</strong>
                      <small>{{ typeLabel(line.itemType) }} · {{ formatTolerance(line) }}</small>
                    </span>
                    <span :class="['std-req', line.requiredFlag ? 'must' : 'opt']">
                      {{ line.requiredFlag ? '必检' : '选检' }}
                    </span>
                  </button>

                  <div v-show="isLineExpanded(idx)" class="std-item-card-body">
                    <template v-if="editingLineIdx === idx">
                      <label class="std-mini-field">
                        <span>检验项</span>
                        <select v-model="line.inspectItemId" @change="onLineItemChange(line)">
                          <option
                            v-for="item in enabledInspectItems"
                            :key="item.inspectItemId"
                            :value="item.inspectItemId"
                          >{{ item.itemCode }} / {{ item.itemName }}</option>
                        </select>
                      </label>
                      <label class="std-mini-field">
                        <span>判定标准</span>
                        <input v-model="line.standardText" />
                      </label>
                      <div class="std-mini-grid">
                        <label class="std-mini-field">
                          <span>标称</span>
                          <input v-model.number="line.nominal" type="number" step="0.001" />
                        </label>
                        <label class="std-mini-field">
                          <span>下差</span>
                          <input v-model.number="line.lowerTol" type="number" step="0.001" />
                        </label>
                        <label class="std-mini-field">
                          <span>上差</span>
                          <input v-model.number="line.upperTol" type="number" step="0.001" />
                        </label>
                      </div>
                      <div class="std-mini-grid">
                        <label class="std-mini-field">
                          <span>下限</span>
                          <input v-model.number="line.minValue" type="number" step="0.001" />
                        </label>
                        <label class="std-mini-field">
                          <span>上限</span>
                          <input v-model.number="line.maxValue" type="number" step="0.001" />
                        </label>
                        <label class="std-mini-field">
                          <span>单位</span>
                          <input v-model="line.unit" />
                        </label>
                      </div>
                      <div class="std-item-card-actions">
                        <label class="std-check"><input v-model="line.requiredFlag" type="checkbox" :true-value="1" :false-value="0" />必检</label>
                        <label class="std-check"><input v-model="line.criticalFlag" type="checkbox" :true-value="1" :false-value="0" />关键</label>
                        <button type="button" class="std-btn ghost" @click="finishEditLine">完成</button>
                        <button type="button" class="std-btn danger" @click="removeLine(idx)">删除</button>
                      </div>
                    </template>

                    <template v-else>
                      <dl>
                        <div><dt>类型</dt><dd>{{ typeLabel(line.itemType) }}</dd></div>
                        <div><dt>判定标准</dt><dd>{{ line.standardText || '—' }}</dd></div>
                        <div><dt>公差/限值</dt><dd>{{ formatTolerance(line) }}</dd></div>
                        <div><dt>方法</dt><dd>{{ methodLabel(line.itemType) }}</dd></div>
                      </dl>
                      <button type="button" class="std-btn ghost block" @click="startEditLine(idx)">编辑</button>
                    </template>
                  </div>
                </article>
              </div>
              <p v-if="linesSectionOpen && !form.lines.length" class="std-empty">尚未添加检验项目，点击右上角添加。</p>
            </div>
          </div>

          <footer class="std-detail-footer">
            <button type="button" class="std-btn primary" :disabled="saving" @click="saveStandard">
              {{ saving ? '保存中…' : '保存' }}
            </button>
            <button type="button" class="std-btn outline" :disabled="saving" @click="publishStandard">发布标准</button>
            <button type="button" class="std-btn danger" @click="disableCurrent">停用标准</button>
            <button type="button" class="std-btn ghost" @click="exportStandard">导出标准</button>
          </footer>
        </section>
      </template>

      <div v-else class="std-detail-empty">
        <p>请选择左侧标准，或点击「新建标准」开始配置。</p>
      </div>
    </div>
  </section>
</template>
