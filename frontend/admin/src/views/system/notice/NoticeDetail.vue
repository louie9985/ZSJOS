<template>
  <el-dialog v-model="visible" title="公告详情" width="min(800px, 94vw)" destroy-on-close>
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon>
        <el-button link type="primary" @click="load">重试</el-button>
      </el-alert>
      <article v-else-if="notice">
        <el-tabs v-model="activeTab">
          <el-tab-pane name="content" label="公告正文" />
          <el-tab-pane name="reading" label="阅读情况" />
        </el-tabs>
        <NoticeReadStatistics v-if="activeTab === 'reading' && notice.id != null" :key="notice.id" :id="notice.id" />
        <div v-else>
        <h2>{{ notice.title }}</h2>
        <el-button v-if="notice.publishStatus === 'PUBLISHED' && checkPermi(['system:notice:query']) && checkPermi(['system:notice:share'])" @click="sharing = true">对外分享</el-button>
        <NoticeShareDialog v-if="sharing" :notice="notice" @close="sharing = false" />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="公告类型"><dict-tag :type="DICT_TYPE.SYSTEM_NOTICE_TYPE" :value="notice.type" /></el-descriptions-item>
          <el-descriptions-item label="发布状态">{{ statusLabels[notice.publishStatus] }}</el-descriptions-item>
          <el-descriptions-item label="文章来源">{{ notice.sourceDeptName || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ notice.publishStatus === 'DRAFT' ? '发布时自动记录' : notice.publisherName || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="接收部门/人员">{{ NoticeApi.noticeAudienceText(notice) }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ notice.publishTime ? formatDate(notice.publishTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="高亮截止时间">{{ notice.highlightUntil ? formatDate(notice.highlightUntil) : '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-dompurify-html="notice.content || ''" class="notice-detail-content"></div>
        <template v-if="notice.attachments?.length">
          <h3>附件</h3>
          <div v-for="file in notice.attachments" :key="file.infraFileId">
            <a v-if="safeUrl(file.downloadUrl)" :href="safeUrl(file.downloadUrl)" target="_blank" rel="noopener noreferrer">{{ file.fileName }}</a>
            <span v-else>{{ file.fileName }}（文件不可用）</span>
          </div>
        </template>
        </div>
      </article>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import * as NoticeApi from '@/api/system/notice'
import NoticeReadStatistics from './NoticeReadStatistics.vue'
import NoticeShareDialog from './NoticeShareDialog.vue'
import { checkPermi } from '@/utils/permission'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'

const visible = ref(false)
const sharing = ref(false)
const activeTab = ref('content')
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
const open = (id: number) => { sharing.value = false; activeTab.value = 'content'; noticeId.value = id; visible.value = true; void load() }
watch(visible, value => { if (!value) { generation++; notice.value = undefined } })
defineExpose({ open })
</script>

<style scoped>
.notice-detail-content { padding: 16px 0; overflow-wrap: anywhere; }
.notice-detail-content :deep(img), .notice-detail-content :deep(video) { max-width: 100%; height: auto; }
.notice-detail-content :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
</style>
