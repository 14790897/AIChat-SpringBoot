<template>
  <div class="app">
    <AuthView v-if="!token" @login="onLogin" />
    <ChatView v-else :username="username" @logout="onLogout" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getToken, removeToken, setOnUnauthorized } from './api.js'
import AuthView from './components/AuthView.vue'
import ChatView from './components/ChatView.vue'

const token = ref(getToken())
const username = computed(() => {
  if (!token.value) return ''
  try {
    return JSON.parse(atob(token.value.split('.')[1])).sub
  } catch {
    return ''
  }
})

function onLogin(newToken) {
  token.value = newToken
}

function onLogout() {
  removeToken()
  token.value = null
}

onMounted(() => {
  setOnUnauthorized(() => {
    token.value = null
  })
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  background: #f0f2f5;
  height: 100vh;
}
#app {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
