<template>
  <div class="auth-container">
    <div class="auth-card">
      <h2>AI Chat</h2>
      <div class="auth-tabs">
        <button :class="['auth-tab', tab === 'login' && 'active']" @click="tab = 'login'">登录</button>
        <button :class="['auth-tab', tab === 'register' && 'active']" @click="tab = 'register'">注册</button>
      </div>
      <form @submit.prevent="handleSubmit">
        <input class="auth-input" v-model="username" type="text" placeholder="用户名" required>
        <input class="auth-input" v-model="password" type="password" placeholder="密码" required>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '处理中...' : (tab === 'login' ? '登录' : '注册') }}
        </button>
      </form>
      <div v-if="error" class="auth-error">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login, register, getToken } from '../api.js'

const emit = defineEmits(['login'])

const tab = ref('login')
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    if (tab.value === 'login') {
      await login(username.value, password.value)
    } else {
      await register(username.value, password.value)
    }
    emit('login', getToken())
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}
.auth-card {
  background: white;
  border-radius: 12px;
  padding: 32px;
  width: 360px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.auth-card h2 { text-align: center; margin-bottom: 24px; color: #075e54; }
.auth-tabs {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 2px solid #eee;
}
.auth-tab {
  flex: 1;
  padding: 10px;
  text-align: center;
  cursor: pointer;
  border: none;
  background: none;
  font-size: 15px;
  color: #999;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
}
.auth-tab.active { color: #075e54; border-bottom-color: #075e54; font-weight: 600; }
.auth-input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 15px;
  outline: none;
  margin-bottom: 12px;
}
.auth-input:focus { border-color: #075e54; }
.auth-submit {
  width: 100%;
  padding: 12px;
  background: #075e54;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  cursor: pointer;
  margin-top: 4px;
}
.auth-submit:hover { background: #064e46; }
.auth-submit:disabled { background: #a0a0a0; cursor: not-allowed; }
.auth-error { color: #e74c3c; font-size: 13px; margin-top: 8px; text-align: center; }
</style>
