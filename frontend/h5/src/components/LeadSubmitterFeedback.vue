<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { showImagePreview } from 'vant'
import { getLeadSubmitterFeedback, type LeadSubmitterFeedback } from '@/api/lead'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ leadId: number }>()
const route = useRoute()
const root = ref<HTMLElement>()
const rows = ref<LeadSubmitterFeedback[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = await getLeadSubmitterFeedback(props.leadId, page.value)
    rows.value = result.list; total.value = result.total
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '销售反馈加载失败' }
  finally { loading.value = false }
}
onMounted(async () => {
  await load()
  if (route.hash === '#submitter-feedback') {
    await nextTick(); root.value?.scrollIntoView({ block: 'start' })
  }
})
</script>

<template>
  <section id="submitter-feedback" ref="root" class="feedback-section">
    <h3>销售反馈</h3>
    <van-loading v-if="loading" />
    <van-empty v-else-if="error" :description="error" image="error">
      <van-button size="small" @click="load">重试</van-button>
    </van-empty>
    <van-empty v-else-if="!rows.length" description="暂无销售反馈" :image-size="64" />
    <template v-else>
      <article v-for="row in rows" :key="row.id" class="feedback-record">
        <strong>{{ row.salesName || '销售' }}</strong>
        <time>{{ formatDateTime(row.createTime) }}</time>
        <p>{{ row.feedback }}</p>
        <div v-for="file in row.attachments" :key="file.fileId" class="feedback-file">
          <template v-if="file.url">
            <button v-if="file.contentType.startsWith('image/')" type="button"
              @click="showImagePreview({ images: [file.url], closeable: true })">
              <img :src="file.url" :alt="file.originalName" />
            </button>
            <a :href="file.url" target="_blank" rel="noopener noreferrer">{{ file.originalName }}</a>
          </template>
          <span v-else>{{ file.originalName }}（文件不可用）</span>
        </div>
      </article>
      <van-pagination v-if="total > 10" v-model="page" :total-items="total" :items-per-page="10" @change="load" />
    </template>
  </section>
</template>

<style scoped>
.feedback-section { padding: 16px; scroll-margin-top: 54px; }
h3 { font-size: 15px; margin: 0 0 12px; }
.feedback-record { padding: 12px 0; border-bottom: 1px solid var(--h5-divider); overflow-wrap: anywhere; }
time { display: block; font-size: 12px; color: var(--h5-text-secondary); }
p { white-space: pre-wrap; line-height: 1.6; }
.feedback-file { margin-top: 8px; }
.feedback-file button { border: 0; padding: 0; display: block; background: transparent; }
.feedback-file img { width: 88px; height: 88px; object-fit: cover; }
a { color: var(--h5-primary); }
</style>
