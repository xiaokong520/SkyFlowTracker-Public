<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'
import { sendChatMessageStream, getConversations, getConversationHistory, deleteConversation } from '../api/ai'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ breaks: true, linkify: true })
const renderMd = (content) => content ? md.render(content) : ''

const toast = useToast()
const inputMessage = ref('')
const sending = ref(false)
const currentConversationId = ref(null)
const conversations = ref([])
const messages = ref([])
const chatContainer = ref(null)
const conversationsLoading = ref(false)
const showDeleteDialog = ref(false)
const deleteTargetId = ref(null)

// 流式内容使用独立 ref，确保 v-html 响应式更新
const streamingContent = ref('')
const streamingHtml = computed(() => streamingContent.value ? md.render(streamingContent.value) : '')

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

const loadConversations = async () => {
  conversationsLoading.value = true
  try {
    const res = await getConversations()
    if (res.code === 1) {
      conversations.value = res.data || []
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
  } finally {
    conversationsLoading.value = false
  }
}

const loadHistory = async (conversationId) => {
  currentConversationId.value = conversationId
  messages.value = []
  try {
    const res = await getConversationHistory(conversationId)
    if (res.code === 1) {
      messages.value = (res.data || []).map(msg => ({
        role: msg.role,
        content: msg.content,
        time: msg.createTime
      }))
      scrollToBottom()
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '加载历史消息失败', life: 3000 })
  }
}

const newConversation = () => {
  currentConversationId.value = null
  messages.value = []
}

const handleSend = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || sending.value) return
  inputMessage.value = ''
  messages.value.push({ role: 'user', content: msg })
  scrollToBottom()
  sending.value = true
  streamingContent.value = ''

  sendChatMessageStream(msg, currentConversationId.value, {
    onConversationId: (convId) => {
      currentConversationId.value = convId
    },
    onChunk: (chunk) => {
      streamingContent.value += chunk
      scrollToBottom()
    },
    onDone: () => {
      // 流式完成后，将内容写入 messages 数组
      messages.value.push({ role: 'assistant', content: streamingContent.value })
      streamingContent.value = ''
      sending.value = false
      loadConversations()
      scrollToBottom()
    },
    onError: (err) => {
      // 如果已有部分内容，保留到 messages
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value })
      }
      streamingContent.value = ''
      sending.value = false
      toast.add({ severity: 'error', summary: '错误', detail: err || 'AI 服务请求失败', life: 3000 })
    }
  })
}

const confirmDelete = (convId) => {
  deleteTargetId.value = convId
  showDeleteDialog.value = true
}

const handleDelete = async () => {
  showDeleteDialog.value = false
  try {
    const res = await deleteConversation(deleteTargetId.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 2000 })
      if (currentConversationId.value === deleteTargetId.value) {
        newConversation()
      }
      loadConversations()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const previewText = (text) => {
  if (!text) return '新会话'
  return text.length > 20 ? text.substring(0, 20) + '...' : text
}

onMounted(() => {
  loadConversations()
})
</script>

<template>
  <div class="ai-chat-root flex overflow-hidden">
    <Toast />

    <!-- 左侧会话列表 -->
    <div class="w-72 bg-white border-r border-gray-200 flex flex-col min-h-0 rounded-l-2xl shadow-lg">
      <div class="p-4 border-b border-gray-200">
        <Button
          label="新建会话"
          icon="pi pi-plus"
          @click="newConversation"
          class="!w-full !bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
          size="small"
        />
      </div>

      <div class="flex-1 overflow-y-auto">
        <div v-if="conversationsLoading" class="flex justify-center py-8">
          <i class="pi pi-spin pi-spinner text-2xl text-blue-600"></i>
        </div>
        <div v-else-if="conversations.length === 0" class="text-center py-8 text-gray-400 text-sm">
          暂无会话记录
        </div>
        <div v-else>
          <div
            v-for="conv in conversations"
            :key="conv.conversation_id"
            @click="loadHistory(conv.conversation_id)"
            :class="[
              'flex items-center justify-between px-4 py-3 cursor-pointer transition-colors border-b border-gray-50',
              currentConversationId === conv.conversation_id
                ? 'bg-blue-50 text-blue-700'
                : 'hover:bg-gray-50 text-gray-700'
            ]"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm truncate">{{ previewText(conv.last_message) }}</p>
            </div>
            <button
              @click.stop="confirmDelete(conv.conversation_id)"
              class="ml-2 text-gray-400 hover:text-red-500 cursor-pointer flex-shrink-0"
            >
              <i class="pi pi-trash text-xs"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="flex-1 flex flex-col min-h-0 bg-white rounded-r-2xl shadow-lg">
      <!-- 聊天头部 -->
      <div class="h-14 flex items-center px-6 border-b border-gray-200">
        <i class="pi pi-microchip-ai text-blue-600 text-xl mr-2"></i>
        <h3 class="text-lg font-semibold text-gray-800">AI 智能交通分析助手</h3>
      </div>

      <!-- 消息区域 -->
      <div ref="chatContainer" class="flex-1 overflow-y-auto p-6 space-y-4">
        <div v-if="messages.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400">
          <i class="pi pi-comments text-6xl mb-4"></i>
          <p class="text-lg">开始与 AI 助手对话</p>
          <p class="text-sm mt-1">你可以询问交通数据分析、检测趋势等问题</p>
        </div>

        <div v-for="(msg, index) in messages" :key="index"
          :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']"
        >
          <div :class="[
            'max-w-[70%] rounded-2xl px-4 py-3 text-sm leading-relaxed',
            msg.role === 'user'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-100 text-gray-800'
          ]">
            <div v-if="msg.role === 'user'" class="whitespace-pre-wrap break-words">{{ msg.content }}</div>
            <div v-else class="markdown-body break-words" v-html="renderMd(msg.content)"></div>
          </div>
        </div>

        <!-- 流式输出中的 AI 消息（独立响应式渲染） -->
        <div v-if="sending && streamingContent" class="flex justify-start">
          <div class="max-w-[70%] rounded-2xl px-4 py-3 text-sm leading-relaxed bg-gray-100 text-gray-800">
            <div class="markdown-body break-words" v-html="streamingHtml"></div>
          </div>
        </div>

        <div v-if="sending && !streamingContent" class="flex justify-start">
          <div class="bg-gray-100 rounded-2xl px-4 py-3 text-sm text-gray-500">
            <i class="pi pi-spin pi-spinner mr-1"></i> AI 正在思考...
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="border-t border-gray-200 p-4">
        <div class="flex items-end gap-3">
          <textarea
            v-model="inputMessage"
            @keydown="handleKeydown"
            placeholder="输入消息，按 Enter 发送，Shift+Enter 换行"
            rows="2"
            class="flex-1 resize-none border-2 border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-blue-500 transition-colors"
            :disabled="sending"
          ></textarea>
          <Button
            icon="pi pi-send"
            @click="handleSend"
            :loading="sending"
            :disabled="!inputMessage.trim() || sending"
            class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-xl !h-12 !w-12"
          />
        </div>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <Dialog v-model:visible="showDeleteDialog" modal header="确认删除" :style="{ width: '400px' }">
      <p class="text-sm text-gray-700">确定要删除这个会话吗？删除后无法恢复。</p>
      <template #footer>
        <Button label="取消" @click="showDeleteDialog = false" outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg" />
        <Button label="删除" @click="handleDelete" severity="danger"
          class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.ai-chat-root {
  position: absolute;
  inset: 0;
}

:deep(.p-dialog) {
  background-color: #ffffff !important;
}
:deep(.p-dialog .p-dialog-header) {
  background-color: #ffffff !important;
  color: #1F2937 !important;
}
:deep(.p-dialog .p-dialog-content) {
  background-color: #ffffff !important;
  color: #1F2937 !important;
}
:deep(.p-dialog .p-dialog-footer) {
  background-color: #ffffff !important;
}

/* Markdown 渲染样式 */
.markdown-body :deep(p) {
  margin: 0.4em 0;
  line-height: 1.6;
}
.markdown-body :deep(p:first-child) {
  margin-top: 0;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(strong) {
  font-weight: 700;
}
.markdown-body :deep(em) {
  font-style: italic;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  font-weight: 700;
  margin: 0.6em 0 0.3em;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 1.25em; }
.markdown-body :deep(h2) { font-size: 1.15em; }
.markdown-body :deep(h3) { font-size: 1.05em; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.5em;
}
.markdown-body :deep(ul) { list-style-type: disc; }
.markdown-body :deep(ol) { list-style-type: decimal; }
.markdown-body :deep(li) {
  margin: 0.2em 0;
}
.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin: 0.1em 0;
}
.markdown-body :deep(code) {
  background-color: rgba(0, 0, 0, 0.06);
  padding: 0.15em 0.35em;
  border-radius: 4px;
  font-size: 0.9em;
  font-family: 'Courier New', Courier, monospace;
}
.markdown-body :deep(pre) {
  background-color: #1e1e1e;
  color: #d4d4d4;
  padding: 0.75em 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.5em 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #d1d5db;
  padding-left: 0.75em;
  margin: 0.4em 0;
  color: #6b7280;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 0.6em 0;
}
.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
  width: 100%;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #d1d5db;
  padding: 0.35em 0.6em;
  text-align: left;
}
.markdown-body :deep(th) {
  background-color: #f3f4f6;
  font-weight: 600;
}
</style>
