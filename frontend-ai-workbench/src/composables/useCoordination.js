import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api'

const myNotices = ref([])
const coordinationLoading = ref(false)
const coordinationError = ref('')
const lastSentNotice = ref(null)

const NOTICE_TYPE_LABEL = {
  URGE_OUTBOUND: '催办出库',
  REPLENISH_ADVICE: '补料/调拨',
  REVIEW_FOLLOW: '复核跟进'
}

export function useCoordination() {
  const unreadCount = computed(
    () => myNotices.value.filter((n) => n.status !== 'DONE').length
  )

  /** PMC 发起协同通知（催出库 / 补料调拨 / 复核跟进） */
  async function sendCoordinationNotice(payload = {}) {
    coordinationError.value = ''
    try {
      const notice = await apiPost('/pmc/coordination/notice', {
        noticeType: payload.noticeType,
        title: payload.title,
        content: payload.content,
        planId: payload.planId ?? null,
        planNo: payload.planNo ?? null,
        requisitionId: payload.requisitionId ?? null,
        outboundNo: payload.outboundNo ?? null,
        exceptionTitle: payload.exceptionTitle ?? null,
        targetRole: payload.targetRole ?? 'WAREHOUSE',
        createdBy: payload.createdBy ?? null
      })
      lastSentNotice.value = notice
      myNotices.value = [notice, ...myNotices.value.filter((n) => n.noticeId !== notice.noticeId)]
      return notice
    } catch (error) {
      coordinationError.value = error.message || '发送协同通知失败'
      throw error
    }
  }

  /** PMC 加载自己发起的通知 */
  async function loadMyNotices(createdBy) {
    coordinationLoading.value = true
    coordinationError.value = ''
    try {
      const list = await apiGet('/pmc/coordination/notices/mine', { createdBy })
      myNotices.value = Array.isArray(list) ? list : []
    } catch (error) {
      coordinationError.value = error.message || '加载协同通知失败'
    } finally {
      coordinationLoading.value = false
    }
  }

  /** 标记协同通知已处理（仓管侧） */
  async function handleNotice(noticeId, handledBy) {
    return apiPost(`/pmc/coordination/notice/${noticeId}/handle`, { handledBy })
  }

  /** 标记协同通知已读 */
  async function readNotice(noticeId) {
    return apiPost(`/pmc/coordination/notice/${noticeId}/read`)
  }

  function noticeTypeLabel(type) {
    return NOTICE_TYPE_LABEL[type] || '协同通知'
  }

  return {
    myNotices,
    coordinationLoading,
    coordinationError,
    lastSentNotice,
    unreadCount,
    sendCoordinationNotice,
    loadMyNotices,
    handleNotice,
    readNotice,
    noticeTypeLabel
  }
}
