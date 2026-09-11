<script setup>
import { useRouter } from 'vue-router'
import { demoAccounts } from '../data/accounts'
import { useSession } from '../composables/useSession'
import { usePmc } from '../composables/usePmc'

const router = useRouter()
const { loginForm, loginError, login, fillDemo } = useSession()
const { loadPmcData } = usePmc()

async function submitLogin() {
  const roleId = await login()
  if (!roleId) return
  if (roleId === 'pmc') {
    loadPmcData()
  }
  router.push('/workbench')
}
</script>

<template>
  <div class="login-shell">
    <section class="login-hero">
      <div class="brand login-brand">
        <span class="brand-mark">W</span>
        <strong>WellLog WMS</strong>
      </div>
      <h1>测井装备智能仓储<br />协同调度平台</h1>
      <ul class="login-points">
        <li>按岗位授权，只看你该看的业务</li>
        <li>AI 专家团队协同调度，秒级响应</li>
        <li>收货、质检、库存、出库全链路可追溯</li>
      </ul>
    </section>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="submitLogin">
        <h2>账号登录</h2>
        <p class="login-sub">输入岗位账号与密码进入工作台</p>

        <label class="login-field">
          <span>账号</span>
          <input v-model="loginForm.username" type="text" autocomplete="username" placeholder="请输入账号" />
        </label>
        <label class="login-field">
          <span>密码</span>
          <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>

        <p v-if="loginError" class="login-error">{{ loginError }}</p>

        <button class="login-submit" type="submit">登录</button>

        <div class="login-demo">
          <span>演示账号（点击自动填充）</span>
          <div class="demo-grid">
            <button
              v-for="account in demoAccounts"
              :key="account[0]"
              type="button"
              @click="fillDemo(account[0], account[1])"
            >
              <strong>{{ account[2] }}</strong>
              <small>{{ account[0] }} / {{ account[1] }}</small>
            </button>
          </div>
        </div>
      </form>
    </section>
  </div>
</template>
