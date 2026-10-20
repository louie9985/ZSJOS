<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Qrcode } from '@/components/Qrcode'
defineProps<{ url: string }>()
const emit = defineEmits<{ error: [string] }>()
const card = ref<HTMLElement>()
const saving = ref(false)
const copying = ref(false)
async function renderImage() {
  const qr = card.value?.querySelector('canvas')
  if (!qr) throw Error('二维码未就绪')
  const logo = new Image()
  logo.src = '/collection/logo.png'
  await logo.decode()
  const canvas = document.createElement('canvas')
  canvas.width = 800
  canvas.height = 1080
  const c = canvas.getContext('2d')
  if (!c) throw Error('无法创建图片')
  c.fillStyle = '#fff'
  c.fillRect(0, 0, 800, 1080)
  c.drawImage(logo, 110, 72, 580, (580 * logo.naturalHeight) / logo.naturalWidth)
  c.textAlign = 'center'
  c.fillStyle = '#18324d'
  c.font = '600 38px "Microsoft YaHei",sans-serif'
  c.fillText('学员报名信息收集表', 400, 302)
  c.fillStyle = '#607389'
  c.font = '23px "Microsoft YaHei",sans-serif'
  c.fillText('扫码填写，完成本次报名资料提交', 400, 354)
  c.fillStyle = '#f4f8fc'
  c.fillRect(139, 382, 522, 522)
  c.fillStyle = '#fff'
  c.fillRect(157, 400, 486, 486)
  c.imageSmoothingEnabled = false
  c.drawImage(qr, 169, 412, 462, 462)
  c.fillStyle = '#607389'
  c.font = '22px "Microsoft YaHei",sans-serif'
  c.fillText('微信扫一扫 · 手机与电脑均可填写', 400, 958)
  c.font = '20px "Microsoft YaHei",sans-serif'
  c.fillText('请核对后提交，每份表单仅可提交一次', 400, 1004)
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((v) => (v ? resolve(v) : reject(Error('导出失败'))), 'image/png')
  )
}
async function save() {
  emit('error', '')
  saving.value = true
  try {
    const blob = await renderImage()
    const src = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = src
    a.download = '学员报名信息收集表.png'
    a.click()
    setTimeout(() => URL.revokeObjectURL(src), 1000)
  } catch (e) {
    emit('error', e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function copy() {
  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    emit('error', '当前浏览器不支持复制图片，请保存图片后发送')
    return
  }
  emit('error', '')
  copying.value = true
  try {
    const image = renderImage()
    // Keep the write inside the click gesture even while the PNG is being generated.
    // Rendering can still fail after the browser has already refused clipboard access.
    void image.catch(() => undefined)
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': image })])
    ElMessage.success('图片已复制，可粘贴发送')
  } catch {
    emit('error', '复制图片失败，请允许剪贴板访问后重试，或保存图片后发送')
  } finally {
    copying.value = false
  }
}
</script>
<template>
  <div class="collection-qr"
    ><div ref="card" class="collection-qr-card" aria-label="学员报名信息收集表二维码卡片"
      ><img src="/collection/logo.png" alt="中世健教育" /><h3>学员报名信息收集表</h3
      ><p>扫码填写，完成本次报名资料提交</p
      ><div class="collection-qr-code"
        ><Qrcode
          :text="url"
          :width="232"
          :options="{ margin: 4, color: { dark: '#142e47', light: '#ffffff' } }" /></div
      ><p>微信扫一扫 · 手机与电脑均可填写</p><small>请核对后提交，每份表单仅可提交一次</small></div
    ><el-button type="primary" :loading="copying" :disabled="saving || copying" @click="copy"
      >复制图片</el-button
    ><el-button :loading="saving" :disabled="saving || copying" @click="save"
      >保存二维码图片</el-button
    ></div
  >
</template>
<style scoped>
.collection-qr {
  width: 100%;
  max-width: 390px;
  margin: 12px auto;
  text-align: center;
}
.collection-qr-card {
  border: 1px solid #e3ebf3;
  border-radius: 16px;
  background: #fff;
  color: #18324d;
  padding: 24px 16px;
  margin-bottom: 16px;
}
.collection-qr-card img {
  width: 88%;
  max-width: 290px;
  height: auto;
  display: block;
  margin: 0 auto 20px;
}
.collection-qr-card h3 {
  font-size: 22px;
  margin: 0 0 8px;
}
.collection-qr-card p {
  font-size: 13px;
  color: #607389;
  margin: 12px 0 20px;
}
.collection-qr-card small {
  color: #8492a3;
  font-size: 12px;
}
.collection-qr-code {
  width: fit-content;
  padding: 14px;
  border-radius: 16px;
  background: #f4f8fc;
  margin: auto;
}
.collection-qr > .el-button {
  width: 100%;
}
.collection-qr > .el-button + .el-button {
  margin: 8px 0 0;
}
</style>
