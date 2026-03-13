<template>
  <div class="auth-container">
    <el-card class="auth-card" shadow="always">
      <template #header>
        <h2 class="auth-title">AI Chat</h2>
      </template>
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>
      <el-form @submit.prevent="handleSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" :prefix-icon="Lock"
            size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" size="large" style="width: 100%">
            {{ tab === 'login' ? '登录' : '注册' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
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
  width: 400px;
}
.auth-title {
  text-align: center;
  color: var(--el-color-primary);
  margin: 0;
}
</style>
