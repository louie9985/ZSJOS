<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getLeadDetail, supplementLead, uploadLeadAttachment, type UploadResult } from '@/api/lead'
import ImageUploader from '@/components/ImageUploader.vue'
import { createIdempotencyKey } from '@/utils/idempotency'
const route = useRoute(); const router = useRouter(); const leadId = Number(route.params.id)
const remark = ref(''); const loading = ref(false); const dataLoading = ref(true); const dataError = ref(''); const uploader = ref<InstanceType<typeof ImageUploader>>(); let supplementIdempotencyKey = createIdempotencyKey(); let submittedFingerprint = ''
async function load() { dataLoading.value = true; dataError.value = ''; try { await getLeadDetail(leadId) } catch { dataError.value = '客资资料加载失败，请重试' } finally { dataLoading.value = false } }
load()
async function handleSubmit() { const text = remark.value.trim(); if (!text) return showToast('请填写补充说明'); if (loading.value) return; if (uploader.value?.isUploading()) return showToast('图片上传中，请稍候'); if (uploader.value?.hasError()) return showToast('有图片上传失败，请重试'); loading.value = true; try { const files = uploader.value?.getUploadedFiles() || []; const fingerprint = JSON.stringify([text, files.map(file => file.infraFileId)]); if (submittedFingerprint && submittedFingerprint !== fingerprint) supplementIdempotencyKey = createIdempotencyKey(); submittedFingerprint = fingerprint; await supplementLead(leadId, { remark: text, attachments: files.map(file => ({ infraFileId: file.infraFileId })), idempotencyKey: supplementIdempotencyKey }); showSuccessToast('补充资料已提交'); router.back() } catch (cause) { showToast(cause instanceof Error ? cause.message : '提交失败，请重试') } finally { loading.value = false } }
</script>
<template><div class="page-container"><van-nav-bar title="补充资料" left-arrow @click-left="router.back" /><van-skeleton :loading="dataLoading" :row="3" style="padding:16px"><van-empty v-if="dataError" image="error" :description="dataError"><van-button size="small" type="primary" @click="load">重新加载</van-button></van-empty><template v-else><div class="card"><van-field v-model="remark" label="补充说明" type="textarea" required maxlength="1000" show-word-limit rows="6" autosize placeholder="请输入补充说明" /><div class="field-label">图片附件（可选）</div><p class="field-desc">最多 9 张，支持 JPG、PNG、WebP</p><ImageUploader ref="uploader" :max-count="9" /></div><div style="padding:16px"><van-button type="primary" block round :loading="loading" @click="handleSubmit">提交补充</van-button></div></template></van-skeleton></div></template>
