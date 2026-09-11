<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRoleAccess } from '../../composables/useRoleAccess'
import { useQuality } from '../../composables/useQuality'
import {
  ISSUE_STAGE,
  getIssueActionsForStage,
  getIssueStageHint
} from '../../data/qualityConstants'

const route = useRoute()
const router = useRouter()
const { allowed } = useRoleAccess('quality')
const {
  qualityIssues,
  qualityDataLoading,
  qualityDataError,
  selectedIssue,
  currentIssue,
  issueDetail,
  loadQualityData,
  loadIssueDetail,
  executeIssueAction,
  issueLevelClass,
  issueStatusClass,
  selectIssueById
} = useQuality()

const actionLoading = ref('')
const actionMessage = ref('')
const detailOpen = ref(false)
const activeTab = ref('records')
const statusFilter = ref('all')
const levelFilter = ref('all')
const typeFilter = ref('all')
const keyword = ref('')

const tabs = [
  { key: 'records', label: '异常记录' },
  { key: 'mine', label: '我发起的' },
  { key: 'confirm', label: '待确认' },
  { key: 'closed', label: '已处理' }
]

const severityOptions = [
  { value: 'all', label: '全部等级' },
  { value: '高', label: '严重' },
  { value: '中', label: '一般' },
  { value: '低', label: '轻微' }
]

const statusOptions = computed(() => ['all', ...new Set(qualityIssues.value.map((issue) => issue.status).filter(Boolean))])
const typeOptions = computed(() => ['all', ...new Set(qualityIssues.value.map((issue) => issue.issueType).filter(Boolean))])

const filteredIssues = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return qualityIssues.value.filter((issue) => {
    const status = issue.status || ''
    const level = issue.riskLevel || issue.level || ''
    const tabMatched =
      activeTab.value === 'records' ||
      (activeTab.value === 'confirm' && status !== '已关闭') ||
      (activeTab.value === 'closed' && status === '已关闭') ||
      (activeTab.value === 'mine' && status !== '已关闭')
    const statusMatched = statusFilter.value === 'all' || status === statusFilter.value
    const levelMatched = levelFilter.value === 'all' || level.includes(levelFilter.value)
    const typeMatched = typeFilter.value === 'all' || issue.issueType === typeFilter.value
    const text = [issue.issueNo, issue.itemName, issue.batchNo, issue.issueType, issue.issueDesc, issue.status]
      .join(' ')
      .toLowerCase()
    const keywordMatched = !kw || text.includes(kw)
    return tabMatched && statusMatched && levelMatched && typeMatched && keywordMatched
  })
})

const selectedIssueId = computed(() => currentIssue.value?.issueId)
const selectedIssueIndexInFiltered = computed(() => filteredIssues.value.findIndex((issue) => issue.issueId === selectedIssueId.value))

const inspectionLine = computed(() => issueDetail.value?.inspectionLine)
const inspection = computed(() => issueDetail.value?.inspection)
const item = computed(() => issueDetail.value?.item)
const batch = computed(() => issueDetail.value?.batch)
const supplier = computed(() => issueDetail.value?.supplier)
const receiptLine = computed(() => issueDetail.value?.receiptLine)
const inventories = computed(() => issueDetail.value?.inventories ?? [])

const materialStage = computed(() => {
  if (issueDetail.value?.materialStage) return issueDetail.value.materialStage
  const hasStock = inventories.value.some((inv) =>
    Number(inv.onhandQty ?? 0) > 0 || Number(inv.availableQty ?? 0) > 0
  )
  return hasStock ? ISSUE_STAGE.SHELVED : ISSUE_STAGE.PRE_PUTAWAY
})

const isPrePutaway = computed(() => materialStage.value === ISSUE_STAGE.PRE_PUTAWAY)
const availableActions = computed(() => getIssueActionsForStage(materialStage.value))
const stageHint = computed(() => getIssueStageHint(materialStage.value))
const stageLabel = computed(() =>
  isPrePutaway.value ? '未入库（来料检不合格）' : '已入库（在库批次）'
)

const inspectionSummary = computed(() => {
  const inspected = Number(inspectionLine.value?.inspectedQty ?? 0)
  const qualified = Number(inspectionLine.value?.qualifiedQty ?? 0)
  const unqualified = Number(inspectionLine.value?.unqualifiedQty ?? currentIssue.value?.unqualifiedQty ?? 0)
  const rate = inspected ? Math.round((qualified / inspected) * 1000) / 10 : 0
  return { inspected, qualified, unqualified, rate }
})

const actionMessages = {
  freeze: (result) => `库存冻结申请已提交。${result?.message || ''}`,
  return: (result) => result?.message || '供应商退货申请已提交，等待采购/QE 确认。',
  repair: (result) => result?.message || '返修工单申请已提交，等待 QE/生产确认。',
  isolate: (result) => result?.message || '已标记禁止上架并转入隔离待退。'
}

onMounted(async () => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('质量异常'))
    return
  }
  await loadQualityData()
  const issueId = Number(route.query.issueId)
  if (issueId) {
    selectIssueById(issueId)
    await openIssueDetail(qualityIssues.value.find((row) => row.issueId === issueId))
  }
})

watch(selectedIssue, async () => {
  if (detailOpen.value && currentIssue.value) {
    await loadIssueDetail(currentIssue.value.issueId)
  }
})

async function openIssueDetail(issue) {
  if (!issue) return
  const index = qualityIssues.value.findIndex((row) => row.issueId === issue.issueId)
  if (index >= 0) selectedIssue.value = index
  detailOpen.value = true
  actionMessage.value = ''
  await loadIssueDetail(issue.issueId)
}

function closeDetailModal() {
  detailOpen.value = false
  actionMessage.value = ''
}

async function handleAction(action) {
  if (!currentIssue.value) return
  actionLoading.value = action.key
  actionMessage.value = ''
  try {
    const result = await executeIssueAction(action.key, currentIssue.value.issueId)
    if (action.key === 'close') {
      actionMessage.value = '异常已关闭。'
      await loadQualityData(true)
      closeDetailModal()
    } else {
      const format = actionMessages[action.key]
      actionMessage.value = format ? format(result) : `${action.label}已提交。`
    }
  } catch (error) {
    actionMessage.value = error.message || '操作失败'
  } finally {
    actionLoading.value = ''
  }
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign">
    <p v-if="qualityDataLoading" class="data-hint">正在加载异常数据...</p>
    <p v-else-if="qualityDataError" class="data-hint danger">{{ qualityDataError }}</p>
    <p v-if="actionMessage && !detailOpen" class="data-hint">{{ actionMessage }}</p>

    <header class="qa-issue-header qa-issue-header-title-only">
      <div>
        <h1>异常处理中心 <span>i</span></h1>
      </div>
    </header>

    <section class="qa-records-panel">
      <div class="qa-tabs" role="tablist" aria-label="质量异常分类">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >{{ tab.label }}</button>
      </div>

      <div class="qa-filter-row">
        <label><span>状态：</span><select v-model="statusFilter"><option value="all">全部状态</option><option v-for="status in statusOptions.filter((s) => s !== 'all')" :key="status" :value="status">{{ status }}</option></select></label>
        <label><span>严重等级：</span><select v-model="levelFilter"><option v-for="level in severityOptions" :key="level.value" :value="level.value">{{ level.label }}</option></select></label>
        <label><span>异常类型：</span><select v-model="typeFilter"><option value="all">全部类型</option><option v-for="type in typeOptions.filter((t) => t !== 'all')" :key="type" :value="type">{{ type }}</option></select></label>
        <label class="qa-search"><input v-model="keyword" type="search" placeholder="请输入问题编号/物料/供应商" /><span>⌕</span></label>
      </div>

      <div class="qa-issue-table">
        <div class="qa-issue-tr qa-issue-th">
          <span></span><span>问题编号</span><span>物料</span><span>批次</span><span>异常类型</span><span>严重等级</span><span>状态</span><span>发现时间</span><span class="qa-issue-ops">操作</span>
        </div>
        <div
          v-for="(issue, index) in filteredIssues"
          :key="issue.issueId"
          :class="['qa-issue-tr', { selected: issue.issueId === selectedIssueId }]"
        >
          <span><i :class="{ on: issue.issueId === selectedIssueId || (selectedIssueIndexInFiltered < 0 && index === 0) }"></i></span>
          <span class="qa-code">{{ issue.issueNo }}</span>
          <span>{{ issue.itemName }}</span>
          <span>{{ issue.batchNo }}</span>
          <span>{{ issue.issueType || issue.issueDesc }}</span>
          <span><em :class="['qa-pill', issueLevelClass(issue.riskLevel || issue.level)]">{{ (issue.riskLevel || issue.level || '一般').replace('高', '严重').replace('中', '一般') }}</em></span>
          <span><em :class="['qa-dot-status', issueStatusClass(issue.status)]">{{ issue.status }}</em></span>
          <span>{{ issue.discoveredAt || issue.createdAt || '2026-07-10 10:41' }}</span>
          <span class="qa-issue-ops">
            <button type="button" class="qa-view" @click="openIssueDetail(issue)">查看</button>
          </span>
        </div>
        <p v-if="!filteredIssues.length" class="qa-empty">暂无符合条件的质量异常单</p>
      </div>
    </section>

    <Teleport to="body">
      <div
        v-if="detailOpen && currentIssue"
        class="qa-issue-modal-overlay"
        @click.self="closeDetailModal"
      >
        <div class="qa-issue-modal" role="dialog" aria-modal="true" aria-labelledby="qa-issue-modal-title">
          <header class="qa-issue-modal-head">
            <div>
              <h2 id="qa-issue-modal-title">异常基本信息</h2>
              <p>{{ currentIssue.issueNo }} · {{ item?.itemName || currentIssue.itemName }}</p>
            </div>
            <button type="button" class="qa-issue-modal-close" aria-label="关闭" @click="closeDetailModal">×</button>
          </header>

          <div class="qa-issue-modal-body">
            <p v-if="actionMessage" class="data-hint">{{ actionMessage }}</p>

            <section class="qa-card qa-basic-card">
              <dl class="qa-info-list">
                <div><dt>问题编号</dt><dd>{{ currentIssue.issueNo }}</dd></div>
                <div><dt>问题描述</dt><dd>{{ currentIssue.issueDesc || currentIssue.issueType }}</dd></div>
                <div><dt>物料名称</dt><dd>{{ item?.itemName || currentIssue.itemName }}</dd></div>
                <div><dt>批次</dt><dd>{{ batch?.batchNo || currentIssue.batchNo }}</dd></div>
                <div><dt>供应商</dt><dd>{{ supplier?.supplierName || '未关联' }}</dd></div>
                <div><dt>物料阶段</dt><dd><em :class="['qa-pill', isPrePutaway ? 'warn' : 'info']">{{ stageLabel }}</em></dd></div>
                <div><dt>不合格数量</dt><dd>{{ inspectionSummary.unqualified || currentIssue.unqualifiedQty }} 件</dd></div>
                <div><dt>质检编号</dt><dd>{{ inspection?.inspectionNo || '未生成' }}</dd></div>
              </dl>
              <div class="qa-result-block">
                <h3>检验结果</h3>
                <div class="qa-result-grid">
                  <div><span>检验数量</span><strong>{{ inspectionSummary.inspected || '-' }} 件</strong></div>
                  <div><span>合格数量</span><strong>{{ inspectionSummary.qualified || '-' }} 件</strong></div>
                  <div><span>不合格数量</span><strong>{{ inspectionSummary.unqualified || currentIssue.unqualifiedQty || '-' }} 件</strong></div>
                  <div><span>合格率</span><strong class="danger">{{ inspectionSummary.rate || 0 }}%</strong></div>
                </div>
                <p>检验结论 <em class="qa-pill danger">不合格</em></p>
              </div>
              <div v-if="receiptLine?.lineStatus === 'QUARANTINED'" class="qa-inventory-list">
                <h3>收货状态</h3>
                <p class="text-danger">已标记隔离待退，禁止上架</p>
              </div>
              <div v-if="inventories.length" class="qa-inventory-list">
                <h3>关联库存</h3>
                <p v-for="inv in inventories" :key="inv.inventoryId">在库 {{ inv.onhandQty }} / 可用 {{ inv.availableQty }} / 冻结 {{ inv.frozenQty }}</p>
              </div>
            </section>

            <section class="qa-card qa-action-card">
              <h2>异常处理建议确认</h2>
              <p class="qa-stage-hint">{{ stageHint }}</p>
              <div class="qa-action-list">
                <button
                  v-for="action in availableActions"
                  :key="action.key"
                  type="button"
                  :class="['qa-apply-card', action.key]"
                  :disabled="!!actionLoading || currentIssue.status === '已关闭'"
                  @click="handleAction(action)"
                >
                  <span>{{ action.icon }}</span>
                  <strong>{{ actionLoading === action.key ? '处理中...' : action.label }}</strong>
                  <small>{{ action.desc }}</small>
                  <em>发起申请</em>
                </button>
              </div>
              <div class="qa-close-box">
                <button
                  type="button"
                  :disabled="!!actionLoading || currentIssue.status === '已关闭'"
                  @click="handleAction({ key: 'close', label: '关闭异常' })"
                >
                  <span>×</span><strong>{{ actionLoading === 'close' ? '处理中...' : '关闭异常' }}</strong><small>确认无需处理，关闭本异常单</small><em>关闭异常</em>
                </button>
              </div>
            </section>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>
