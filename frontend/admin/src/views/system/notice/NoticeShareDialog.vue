<template>
  <el-dialog :model-value="true" title="对外分享" width="min(680px, 94vw)" :close-on-click-modal="!busy" :show-close="!busy" :close-on-press-escape="!busy" @close="emit('close')">
    <el-alert type="warning" title="获得链接的人均可查看及转发；正文内图片、视频也会公开。" :closable="false" show-icon />
    <p v-if="notice.audienceType === 'TARGET'">此公告原为指定部门／人员可见，开启后外部访问不受原接收范围限制。</p>
    <p v-if="notice.audienceType === 'TARGET'">原内部接收范围：{{ notice.targetDeptIds?.length || 0 }} 个部门，{{ notice.targetUserIds?.length || 0 }} 名指定人员<span v-if="notice.recipientCount != null">，发布时接收人数 {{ notice.recipientCount }}</span>。</p>
    <p>关闭后不能收回已保存的内容；已签发的文件地址可能暂时有效。调整附件需关闭后重新开启，旧二维码不会恢复。</p>
    <details><summary>预览对外正文</summary><h3>{{ notice.title }}</h3><div v-dompurify-html="notice.content || ''" class="share-preview"></div></details>
    <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button :disabled="busy" @click="load">刷新重试</el-button></el-alert>
    <p v-if="feedback" role="status">{{ feedback }}</p>
    <div v-loading="busy" class="share-controls">
      <template v-if="share">
        <el-alert v-if="share.active && !share.url" type="warning" title="外部阅读地址未正确配置，仍可关闭分享。请联系管理员。" :closable="false" />
        <p><strong>公开附件（默认不选择）</strong></p>
        <el-checkbox-group v-if="notice.attachments?.length" v-model="selected" :disabled="busy || share.active">
          <div v-for="file in notice.attachments" :key="file.infraFileId"><el-checkbox :value="file.infraFileId">{{ file.fileName }}</el-checkbox></div>
        </el-checkbox-group>
        <p v-else>本公告没有附件</p>
        <template v-if="share.active && share.url">
          <el-input :model-value="share.url" readonly aria-label="公开链接" />
          <Qrcode :text="share.url" :width="200" :options="{ margin: 1 }" @done="qrData = $event" />
          <div class="share-actions"><el-button @click="copy">复制链接</el-button><el-button :disabled="!qrData" @click="downloadQr">下载二维码</el-button></div>
        </template>
        <div class="share-actions">
          <el-popconfirm v-if="share.active" title="关闭后，原链接和二维码将失效，是否继续？" width="260" @confirm="mutate">
            <template #reference><el-button type="danger" :disabled="busy">关闭分享</el-button></template>
          </el-popconfirm>
          <el-button v-else type="primary" :loading="busy" :disabled="notice.publishStatus !== 'PUBLISHED'" @click="mutate">开启分享</el-button>
        </div>
      </template>
    </div>
    <template #footer><el-button :disabled="busy" @click="emit('close')">返回公告</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Qrcode } from '@/components/Qrcode'
import download from '@/utils/download'
import type { NoticeVO } from '@/api/system/notice'
import { getNoticeShare, openNoticeShare, closeNoticeShare, type NoticeShare } from '@/api/system/notice/share'
const props = defineProps<{ notice: NoticeVO }>()
const emit = defineEmits<{ close: [] }>()
const share = ref<NoticeShare>()
const selected = ref<number[]>([])
const error = ref('')
const feedback = ref('')
const busy = ref(false)
const qrData = ref('')
let generation = 0
const load = async () => {
  if (props.notice.id == null) return
  const current = ++generation
  busy.value = true; error.value = ''; share.value = undefined; qrData.value = ''
  try {
    const result = await getNoticeShare(props.notice.id)
    if (current === generation) { share.value = result; selected.value = result.active ? result.attachmentIds : [] }
  } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '分享配置加载失败' }
  finally { if (current === generation) busy.value = false }
}
const mutate = async () => {
  if (!share.value || busy.value || props.notice.id == null) return
  busy.value = true; error.value = ''; feedback.value = ''
  try {
    if (share.value.active && share.value.version != null) {
      await closeNoticeShare(props.notice.id, share.value.version)
      share.value = { active: false, attachmentIds: [] }; selected.value = []; qrData.value = ''; feedback.value = '分享已关闭，旧链接已失效'
    } else { share.value = await openNoticeShare(props.notice.id, selected.value); feedback.value = '分享已开启' }
  } catch (e) { share.value = undefined; error.value = e instanceof Error ? e.message : '分享操作失败，请刷新配置' }
  finally { busy.value = false }
}
const copy = async () => {
  if (!share.value?.url) return
  try { await navigator.clipboard.writeText(share.value.url); feedback.value = '链接已复制' }
  catch { feedback.value = '复制失败，请在链接框中选中并复制' }
}
const downloadQr = () => { if (qrData.value) download.base64Image(qrData.value, '中世健公告二维码') }
onMounted(load)
onBeforeUnmount(() => generation++)
</script>
<style scoped>
.share-controls { min-height: 80px; }
.share-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.share-preview { overflow-wrap: anywhere; }
.share-preview :deep(img), .share-preview :deep(video) { max-width: 100%; height: auto; }
.share-preview :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
:deep(.el-checkbox) { height: auto; white-space: normal; padding: 6px 0; }
</style>
