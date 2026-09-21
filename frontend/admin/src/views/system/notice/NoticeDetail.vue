<template>
  <el-dialog v-model="visible" title="公告详情" width="min(800px, 94vw)" destroy-on-close>
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon>
        <el-button link type="primary" @click="load">重试</el-button>
      </el-alert>
      <article v-else-if="notice">
        <h2>{{ notice.title }}</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="公告类型"><dict-tag :type="DICT_TYPE.SYSTEM_NOTICE_TYPE" :value="notice.type" /></el-descriptions-item>
          <el-descriptions-item label="发布状态">{{ statusLabels[notice.publishStatus] }}</el-descriptions-item>
          <el-descriptions-item label="接收范围">
            <template v-if="notice.audienceType === 'TARGET'">指定部门/用户（{{ notice.targetDeptIds?.length || 0 }} 个部门，{{ notice.targetUserIds?.length || 0 }} 个指定用户<span v-if="notice.recipientCount != null">，发布时接收人数 {{ notice.recipientCount }}</span>）</template>
            <template v-else>全员</template>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ notice.publishTime ? formatDate(notice.publishTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="高亮截止时间">{{ notice.highlightUntil ? formatDate(notice.highlightUntil) : '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-dompurify-html="notice.content || ''" class="notice-detail-content"></div>
        <h3>附件</h3>
        <el-empty v-if="!notice.attachments?.length" description="无附件" :image-size="48" />
        <div v-for="file in notice.attachments" :key="file.infraFileId">
          <a v-if="safeUrl(file.downloadUrl)" :href="safeUrl(file.downloadUrl)" target="_blank" rel="noopener noreferrer">{{ file.fileName }}</a>
          <span v-else>{{ file.fileName }}（文件不可用）</span>
        </div>
      </article>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import * as NoticeApi from '@/api/system/notice'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const notice = ref<NoticeApi.NoticeVO>()
const noticeId = ref<number>()
let generation = 0
const statusLabels = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }
const safeUrl = (url?: string) => {
  if (!url) return undefined
  try { return ['http:', 'https:'].includes(new URL(url, window.location.origin).protocol) ? url : undefined } catch { return undefined }
}
const load = async () => {
  if (noticeId.value == null) return
  const current = ++generation
  loading.value = true; error.value = ''; notice.value = undefined
  try {
    const data = await NoticeApi.getNotice(noticeId.value)
    if (current === generation) notice.value = data
  } catch (cause) {
    if (current === generation) error.value = cause instanceof Error ? cause.message : '公告详情加载失败'
  } finally { if (current === generation) loading.value = false }
}
const open = (id: number) => { noticeId.value = id; visible.value = true; void load() }
watch(visible, value => { if (!value) { generation++; notice.value = undefined } })
defineExpose({ open })
</script>

<style scoped>
.notice-detail-content { padding: 16px 0; overflow-wrap: anywhere; }
.notice-detail-content :deep(img), .notice-detail-content :deep(video) { max-width: 100%; height: auto; }
.notice-detail-content :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
</style>
