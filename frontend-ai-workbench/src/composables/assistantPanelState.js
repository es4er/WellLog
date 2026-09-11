import { ref } from 'vue'

/** 工作台助手面板状态（按角色隔离，登录/登出时清空） */
export const assistantReply = ref('')
export const assistantIntent = ref('')
export const pendingAssistantAction = ref(null)
/** 产生当前回复的角色 id，避免跨角色串屏 */
export const assistantReplyRoleId = ref('')

export function clearAssistantState() {
  assistantReply.value = ''
  assistantIntent.value = ''
  pendingAssistantAction.value = null
  assistantReplyRoleId.value = ''
}
