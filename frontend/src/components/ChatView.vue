<template>
  <div class="chat-wrapper">
    <div class="header">
      <span>AI Chat</span>
      <div class="header-right">
        <span>{{ username }}</span>
        <button @click="$emit('logout')">退出</button>
      </div>
    </div>
    <div class="chat-container" ref="chatContainer">
      <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
        {{ msg.content }}
      </div>
      <div v-if="thinking" class="message assistant">
        <span class="typing-indicator">思考中...</span>
      </div>
    </div>
    <div class="input-area">
      <textarea
        ref="inputRef"
        v-model="input"
        rows="1"
        placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
        @keydown.enter.exact.prevent="send"
        @input="autoResize"
        :disabled="sending"
      ></textarea>
      <button @click="send" :disabled="sending || !input.trim()">&#9654;</button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { chatStream } from '../api.js'

defineProps({ username: String })
defineEmits(['logout'])

const messages = ref([])
const input = ref('')
const sending = ref(false)
const thinking = ref(false)
const chatContainer = ref(null)
const inputRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  }
}

async function send() {
  const message = input.value.trim()
  if (!message || sending.value) return

  input.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'
  sending.value = true
  thinking.value = true

  messages.value.push({ role: 'user', content: message })
  scrollToBottom()

  try {
    thinking.value = false
    messages.value.push({ role: 'assistant', content: '' })
    // ⚠️ Vue 3 响应式注意：必须通过索引访问数组元素再修改属性，
    // 不能把 push 前保存的原始对象引用（如 const aiMsg = {...}）直接赋值，
    // 那样会绕过 Proxy 的 set 拦截，导致视图不更新。
    // 正确做法：messages.value[index].content = ...（走 Proxy）
    // 错误做法：aiMsg.content = ...（直接改原始对象，Vue 感知不到）
    const aiMsgIndex = messages.value.length - 1

    await chatStream(message, (fullText) => {
      messages.value[aiMsgIndex].content = fullText
      scrollToBottom()
    })
  } catch (e) {
    thinking.value = false
    messages.value.push({ role: 'assistant', content: '出错了: ' + e.message })
  } finally {
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}
</script>

<style scoped>
.chat-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.header {
  background: #075e54;
  color: white;
  padding: 16px 20px;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-right { display: flex; align-items: center; gap: 12px; font-size: 14px; }
.header-right button {
  background: rgba(255,255,255,0.2);
  color: white;
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
}
.header-right button:hover { background: rgba(255,255,255,0.3); }
.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.message {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
  white-space: pre-wrap;
  font-size: 15px;
}
.message.user {
  align-self: flex-end;
  background: #dcf8c6;
  border-bottom-right-radius: 4px;
}
.message.assistant {
  align-self: flex-start;
  background: white;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.1);
}
.input-area {
  background: white;
  padding: 12px 16px;
  display: flex;
  gap: 10px;
  border-top: 1px solid #e0e0e0;
}
.input-area textarea {
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 15px;
  resize: none;
  outline: none;
  font-family: inherit;
  max-height: 120px;
}
.input-area textarea:focus { border-color: #075e54; }
.input-area button {
  background: #075e54;
  color: white;
  border: none;
  border-radius: 50%;
  width: 44px;
  height: 44px;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.input-area button:disabled { background: #a0a0a0; cursor: not-allowed; }
.typing-indicator { color: #999; font-style: italic; font-size: 13px; }
</style>
