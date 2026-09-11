<template>
  <section v-if="allowed" class="worker-process-page">
    <header class="process-header">
      <div>
        <h1>工序流程</h1>
      </div>
      <div class="process-summary">
        <div class="summary-card">
          <span>完成进度</span>
          <strong>{{ completedCount }} / {{ steps.length }}</strong>
          <small>工序</small>
        </div>
        <div class="summary-card">
          <span>状态</span>
          <strong class="status-text">{{ processStatus }}</strong>
        </div>
      </div>
    </header>

    <div class="progress-row">
      <div class="progress-track">
        <div class="progress-value" :style="{ width: `${progressPercent}%` }" />
      </div>
      <strong>{{ progressPercent }}%</strong>
    </div>

    <section class="process-workspace">
      <aside class="step-sidebar">
        <div class="sidebar-title">
          <h2>工序步骤</h2>
          <span>{{ completedCount }}/{{ steps.length }}</span>
        </div>
        <button
          v-for="step in steps"
          :key="step.stepNo"
          type="button"
          class="step-nav-item"
          :class="{
            active: activeStepNo === step.stepNo,
            completed: step.stepStatus === 'COMPLETED',
            processing: step.stepStatus === 'IN_PROGRESS'
          }"
          @click="selectStep(step)"
        >
          <span class="step-node">
            <span v-if="step.stepStatus === 'COMPLETED'">✓</span>
            <span v-else>{{ step.stepNo }}</span>
          </span>
          <span class="step-nav-content">
            <strong>{{ step.stepName }}</strong>
            <small v-if="step.stepStatus === 'COMPLETED'">已完成 {{ step.completedAt }}</small>
            <small v-else-if="step.stepStatus === 'IN_PROGRESS'">当前步骤</small>
            <small v-else>待执行</small>
          </span>
        </button>
      </aside>

      <article v-if="activeStep" class="step-detail-card">
        <div class="detail-heading">
          <div>
            <span class="step-label">步骤 {{ activeStep.stepNo }}</span>
            <h2>{{ activeStep.stepName }}</h2>
          </div>
          <span class="detail-status" :class="statusClass(activeStep.stepStatus)">{{ statusLabel(activeStep.stepStatus) }}</span>
        </div>
        <div class="detail-content">
          <div class="step-image-box">
            <img :src="activeStep.image" :alt="activeStep.stepName" class="step-image" @error="handleImageError" />
          </div>
          <div class="step-guide">
            <section class="guide-section">
              <h3>操作说明</h3>
              <ol>
                <li v-for="(item, index) in activeStep.instructions" :key="index">{{ item }}</li>
              </ol>
            </section>
            <div class="guide-columns">
              <section class="guide-section">
                <h3>关键质量点</h3>
                <ul>
                  <li v-for="(item, index) in activeStep.keyPoints" :key="index">{{ item }}</li>
                </ul>
              </section>
              <section class="guide-section">
                <h3>所需工具</h3>
                <ul>
                  <li v-for="(item, index) in activeStep.tools" :key="index">{{ item }}</li>
                </ul>
              </section>
            </div>
            <div class="safety-tip">
              <strong>安全提示：</strong>{{ activeStep.safetyNotice }}
            </div>
          </div>
        </div>
        <footer class="detail-footer">
          <div class="step-time">
            <span v-if="activeStep.startedAt">开始时间：{{ activeStep.startedAt }}</span>
            <span v-if="activeStep.completedAt">完成时间：{{ activeStep.completedAt }}</span>
          </div>
          <button
            v-if="activeStep.stepStatus === 'IN_PROGRESS'"
            type="button"
            class="complete-button"
            @click="completeStep(activeStep)"
          >
            {{ activeStep.stepNo === steps.length ? '完成全部工序' : '完成并进入下一步' }}
          </button>
          <button v-else-if="activeStep.stepStatus === 'COMPLETED'" type="button" class="complete-button completed-button" disabled>
            该工序已完成
          </button>
          <button v-else type="button" class="complete-button disabled-button" disabled>
            请先完成上一道工序
          </button>
        </footer>
      </article>
    </section>
  </section>
  <RoleAccessDenied v-else required-role-id="worker" module-name="工序流程" />
</template>

<script setup>
import { computed, onActivated, onDeactivated, onMounted, ref } from 'vue'
import { useRoleAccess } from '../composables/useRoleAccess'
import RoleAccessDenied from '../components/RoleAccessDenied.vue'

defineOptions({ name: 'WorkerProcessPage' })

const { allowed } = useRoleAccess('worker')

const PROCESS_TEMPLATE = [
  {
    stepNo: 1, stepCode: 'STEP-01', stepName: '准备工具和物料', image: '/worker-guide/step-01-prepare-tools.png',
    instructions: ['核对生产工单、产品型号和装配数量。', '确认电缆、接头外壳、连接器和紧固件齐全。', '检查工具状态，确保工具能够正常使用。'],
    keyPoints: ['物料型号与工单一致', '物料表面无损伤', '工具状态正常'],
    tools: ['工具箱', '装配工装', '物料清单'],
    safetyNotice: '保持工作台整洁，装配物料应分类摆放。'
  },
  {
    stepNo: 2, stepCode: 'STEP-02', stepName: '检查零件外观', image: '/worker-guide/step-02-inspect-parts.png',
    instructions: ['检查电缆表面是否破损、变形或污染。', '检查连接器及接头外壳是否存在磕碰。', '检查紧固件规格和数量是否正确。'],
    keyPoints: ['外壳无裂纹', '连接器针脚无弯曲', '电缆绝缘层完整'],
    tools: ['照明灯', '放大镜', '清洁布'],
    safetyNotice: '发现破损零件时立即停止使用并隔离。'
  },
  {
    stepNo: 3, stepCode: 'STEP-03', stepName: '安装接头外壳', image: '/worker-guide/step-03-install-shell.png',
    instructions: ['确认接头外壳的装配方向。', '将接头外壳安装到指定位置。', '完成定位并进行初步紧固。'],
    keyPoints: ['装配方向正确', '定位面完全贴合', '外壳无歪斜'],
    tools: ['定位工装', '扭矩扳手', '内六角工具'],
    safetyNotice: '紧固过程中禁止用手触碰旋转工具。'
  },
  {
    stepNo: 4, stepCode: 'STEP-04', stepName: '连接电缆线束', image: '/worker-guide/worker-guide.jpg',
    instructions: ['按照线束颜色及端子编号确认连接位置。', '将端子插入对应接口。', '轻拉线束，确认端子锁扣已经到位。'],
    keyPoints: ['线序与图纸一致', '端子完全插入', '锁扣可靠到位'],
    tools: ['端子工具', '尖嘴钳', '线序图'],
    safetyNotice: '插接前确认设备处于断电状态。'
  },
  {
    stepNo: 5, stepCode: 'STEP-05', stepName: '固定线束卡扣', image: '/worker-guide/step-05-fix-cable-clamp.png',
    instructions: ['按照工艺要求整理线束走向。', '将线束放入固定卡扣。', '检查线束弯曲半径及受力情况。'],
    keyPoints: ['线束走向整齐', '卡扣完全闭合', '线束无拉扯和挤压'],
    tools: ['卡扣钳', '扎带工具', '剪线钳'],
    safetyNotice: '修剪扎带时注意防止尖锐断面划伤手部。'
  },
  {
    stepNo: 6, stepCode: 'STEP-06', stepName: '功能测试', image: '/worker-guide/step-06-function-test.png',
    instructions: ['连接测试设备并确认测试参数。', '执行导通、绝缘和连接可靠性测试。', '确认测试结果合格并完成记录。'],
    keyPoints: ['导通结果正常', '绝缘性能合格', '测试记录完整'],
    tools: ['万用表', '绝缘测试仪', '测试记录表'],
    safetyNotice: '测试过程中禁止直接接触裸露导体。'
  }
]

const steps = ref([])
const activeStepNo = ref(1)
const resetAfterActivated = ref(false)

const activeStep = computed(() => steps.value.find(s => s.stepNo === activeStepNo.value))
const completedCount = computed(() => steps.value.filter(s => s.stepStatus === 'COMPLETED').length)
const progressPercent = computed(() => steps.value.length ? Math.round(completedCount.value / steps.value.length * 100) : 0)
const processStatus = computed(() => completedCount.value === steps.value.length ? '已完成' : '进行中')

function pad(n) { return String(n).padStart(2, '0') }
function formatDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function resetProcess() {
  const now = formatDateTime(new Date())
  steps.value = PROCESS_TEMPLATE.map((s, i) => ({ ...s, stepStatus: i === 0 ? 'IN_PROGRESS' : 'PENDING', startedAt: i === 0 ? now : null, completedAt: null }))
  activeStepNo.value = 1
}

function selectStep(step) { activeStepNo.value = step.stepNo }

function completeStep(step) {
  if (step.stepStatus !== 'IN_PROGRESS') return
  step.stepStatus = 'COMPLETED'
  step.completedAt = formatDateTime(new Date())
  const next = steps.value.find(s => s.stepNo === step.stepNo + 1)
  if (next) {
    next.stepStatus = 'IN_PROGRESS'
    next.startedAt = formatDateTime(new Date())
    activeStepNo.value = next.stepNo
  }
}

function statusLabel(status) {
  return { COMPLETED: '已完成', IN_PROGRESS: '进行中', PENDING: '待执行' }[status] || status
}

function handleImageError(event) {
  if (event.target.src.endsWith('/worker-guide/worker-guide.jpg')) return
  event.target.src = '/worker-guide/worker-guide.jpg'
}

function statusClass(status) {
  return { completed: 'completed', processing: 'processing', pending: 'pending' }[status] || ''
}

onMounted(resetProcess)
onDeactivated(() => { resetAfterActivated.value = true })
onActivated(() => {
  if (resetAfterActivated.value) {
    resetAfterActivated.value = false
    resetProcess()
  }
})
</script>
