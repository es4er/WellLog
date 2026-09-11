import { readFileSync, writeFileSync } from 'fs'

const p = 'c:/Users/es4er/Desktop/soft/wms/frontend-ai-workbench/src/pages/ReplenishmentPage.vue'
let s = readFileSync(p, 'utf8')
const start = s.indexOf('const {\n  REPLENISH_REASONS')
const end = s.indexOf('const openTasks = computed')
if (start < 0 || end < 0) {
  console.error('markers not found', start, end)
  process.exit(1)
}
const replacement = `const {
  REPLENISH_REASONS,
  pickingTasks,
  workerDataLoading,
  loadWorkerData,
  submitReplenish,
  replenishRecords,
  resolvePickingTaskId,
  resolvePickingLineId,
  isTaskHandedOver,
  hasOpenException,
  getTaskException
} = useWorker()

const form = reactive({
  taskId: '',
  itemId: '',
  qty: 1,
  reason: REPLENISH_REASONS[0],
  note: ''
})
const submitting = ref(false)
const submitted = ref(false)
const tip = ref('')
const activeTab = ref('all')
const statusFilter = ref('all')
const keyword = ref('')
const selectedRecordId = ref('')

const PAGE_SIZE = 6
const page = ref(1)

`
s = s.slice(0, start) + replacement + s.slice(end)

// patch handleSubmit
const hsStart = s.indexOf('async function handleSubmit() {')
const hsEnd = s.indexOf('function resetForm() {')
if (hsStart < 0 || hsEnd < 0) {
  console.error('handleSubmit not found')
  process.exit(1)
}
const handleSubmit = `async function handleSubmit() {
  if (!selectedTask.value || !selectedItem.value) {
    tip.value = '请选择关联工单与申请物料'
    return
  }
  submitting.value = true
  tip.value = ''
  try {
    const entry = await submitReplenish({
      pickingTaskId: resolvePickingTaskId(selectedTask.value),
      pickingLineId: resolvePickingLineId(selectedItem.value),
      qty: form.qty,
      reason: form.reason,
      note: form.note,
      workOrder: selectedTask.value.workOrder,
      product: selectedTask.value.product,
      material: selectedItem.value.material,
      materialCode: selectedItem.value.materialCode,
      spec: selectedItem.value.spec || '—'
    })
    selectedRecordId.value = entry.id
    activeTab.value = 'all'
    page.value = 1
    submitted.value = true
    tip.value = \`补料申请已提交：\${entry.workOrder} / \${entry.material} × \${entry.qty}\`
  } catch (error) {
    tip.value = error.message || '提交补料申请失败'
  } finally {
    submitting.value = false
  }
}

`
s = s.slice(0, hsStart) + handleSubmit + s.slice(hsEnd)
writeFileSync(p, s)
console.log('ReplenishmentPage patched')
