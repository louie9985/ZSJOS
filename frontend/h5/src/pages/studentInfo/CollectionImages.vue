<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { showImagePreview, showToast } from 'vant'
import 'vant/es/image-preview/style'
import { collectionApi, type CollectionImage, type CollectionField } from '@/api/studentInfo'
import { createIdempotencyKey } from '@/utils/idempotency'
const props = defineProps<{ token: string; field: CollectionField; images: CollectionImage[]; disabled: boolean }>()
const emit = defineEmits<{ change: [CollectionImage[]]; busy: [boolean]; failed: [boolean] }>()
type Pending = { uid: string; file: File; side: string; status: 'uploading' | 'error'; progress: number; error?: string }
const pending = ref<Pending[]>([])
const urls = ref<Record<number, string>>({})
const localImages = ref<CollectionImage[]>([...props.images])
watch(() => props.images, value => { localImages.value = [...value] }, { deep: true })
const deleting = ref(false)
let generation = 0
const slots = () => props.field.key === 'identity_images' ? [{ side: 'front', label: '身份证正面', max: 1 }, { side: 'back', label: '身份证反面', max: 1 }] : [{ side: '', label: '添加附件', max: props.field.maxImages || 9 }]
const sideImages = (side: string) => localImages.value.filter(x => x.side === side)
const canAdd = (side: string, max: number) => sideImages(side).length + pending.value.filter(x => x.side === side).length < max
function status() { emit('busy', deleting.value || pending.value.some(x => x.status === 'uploading')); emit('failed', pending.value.some(x => x.status === 'error')) }
async function send(item: Pending) {
  const current = generation; item.status = 'uploading'; item.error = ''; status()
  try {
    const image = await collectionApi.upload(props.token, props.field.key, item.side, item.file, value => { item.progress = value })
    if (generation !== current) return
    localImages.value = [...localImages.value, image]; emit('change', localImages.value); pending.value = pending.value.filter(x => x.uid !== item.uid)
  } catch (e) { if (generation === current) { item.status = 'error'; item.error = e instanceof Error ? e.message : '上传失败，请重试' } }
  finally { if (generation === current) status() }
}
function choose(event: Event, side: string, max: number) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || []); input.value = ''
  const available = max - sideImages(side).length - pending.value.filter(x => x.side === side).length
  if (files.length > available) showToast(`此处最多 ${max} 个附件`)
  for (const file of files.slice(0, available)) {
    if (!(props.field.allowedExtensions || ['jpg', 'jpeg', 'png']).includes(file.name.split('.').pop()?.toLowerCase() || '')) { showToast('附件格式不符合此字段要求'); continue }
    if (file.size > (props.field.maxSizeMb || 10) * 1024 * 1024) { showToast(`单个不能超过 ${props.field.maxSizeMb || 10}MB`); continue }
    const item: Pending = { uid: createIdempotencyKey(), file, side, status: 'uploading', progress: 0 }; pending.value.push(item); void send(pending.value[pending.value.length - 1])
  }
}
async function remove(image: CollectionImage) {
  deleting.value = true; status()
  try { await collectionApi.removeImage(props.token, image.id); localImages.value=localImages.value.filter(x => x.id !== image.id); emit('change', localImages.value) }
  catch (e) { showToast(e instanceof Error ? e.message : '删除失败') }
  finally { deleting.value = false; status() }
}
function discard(uid: string) { pending.value = pending.value.filter(x => x.uid !== uid); status() }
watch(() => props.images, async images => {
  const current = generation
  for (const image of images) if (!urls.value[image.id]) {
    try { const url = await collectionApi.image(props.token, image); if (current === generation) urls.value[image.id] = url; else URL.revokeObjectURL(url) } catch { /* attachment metadata remains visible with a retry action */ }
  }
}, { immediate: true, deep: true })
async function preview(image: CollectionImage) {
  try { if (!urls.value[image.id]) urls.value[image.id] = await collectionApi.image(props.token, image); if (image.contentType.startsWith('image/')) showImagePreview({ images: [urls.value[image.id]], closeable: true, className: 'collection-image-preview norem' }); else { const a = document.createElement('a'); a.href = urls.value[image.id]; a.download = image.name; a.click() } }
  catch (e) { showToast(e instanceof Error ? e.message : '图片加载失败') }
}
onBeforeUnmount(() => { generation++; Object.values(urls.value).forEach(URL.revokeObjectURL) })
</script>
<template>
  <div class="collection-images norem">
    <div v-for="slot in slots()" :key="slot.side" class="collection-images__slot norem">
      <span v-if="slot.side" class="collection-images__caption norem">{{ slot.label }}</span>
      <div class="collection-images__list norem">
        <div v-for="image in sideImages(slot.side)" :key="image.id" class="collection-images__item norem">
          <button type="button" class="collection-images__preview norem" :aria-label="`预览${field.label}${slot.side ? slot.label : ''}`" @click="preview(image)"><img v-if="urls[image.id] && image.contentType.startsWith('image/')" :src="urls[image.id]" :alt="field.label + slot.label"><span v-else>{{ image.contentType.startsWith('image/') ? '查看图片' : image.name }}</span></button>
          <button type="button" class="collection-images__delete norem" :disabled="disabled || deleting" :aria-label="`删除${field.label}${slot.label}`" @click="remove(image)">×</button>
        </div>
        <div v-for="item in pending.filter(x => x.side === slot.side)" :key="item.uid" class="collection-images__pending norem">
          <span>{{ item.status === 'uploading' ? `上传 ${item.progress}%` : item.error }}</span>
          <template v-if="item.status === 'error'"><button type="button" :disabled="disabled" @click="send(item)">重试</button><button type="button" :disabled="disabled" @click="discard(item.uid)">移除</button></template>
        </div>
        <label v-if="canAdd(slot.side, slot.max)" class="collection-images__add norem" :class="{ inactive: disabled }"><van-icon name="plus" size="24"/><span>{{ sideImages(slot.side).length }}/{{ slot.max }}</span><input type="file" :accept="(field.allowedExtensions || ['jpg','jpeg','png']).map(e => '.' + e).join(',')" :multiple="slot.max > 1" :disabled="disabled" :aria-label="`${field.label}${slot.side ? slot.label : '添加附件'}`" @change="choose($event, slot.side, slot.max)"></label>
      </div>
    </div>
    <p class="collection-images__hint norem">支持 {{ (field.allowedExtensions || ['jpg','jpeg','png']).join('/').toUpperCase() }}，单个不超过 {{ field.maxSizeMb || 10 }}MB</p>
  </div>
</template>
<style scoped>
/* The teleported image viewer must remain transparent over its own dark overlay. */
:global(.collection-image-preview.norem.van-popup){border:0;background:transparent;box-shadow:none;backdrop-filter:none;-webkit-backdrop-filter:none}
.collection-images__list.norem{display:flex;flex-wrap:wrap;gap:8px}.collection-images__slot.norem{margin-top:8px}.collection-images__caption.norem,.collection-images__hint.norem{font-size:12px;color:var(--h5-text-secondary)}.collection-images__item.norem,.collection-images__add.norem{width:80px;height:80px;position:relative;border-radius:8px;background:var(--h5-glass-sunken);flex-shrink:0}.collection-images__preview.norem{width:100%;height:100%;border:0;background:transparent;padding:0;border-radius:8px;overflow:hidden;color:var(--h5-text-secondary)}.collection-images__preview img{width:100%;height:100%;object-fit:cover}.collection-images__delete.norem{position:absolute;right:-4px;top:-4px;min-width:28px;min-height:28px;border:0;border-radius:50%;background:var(--h5-text-primary);color:var(--h5-card-bg)}.collection-images__add.norem{display:flex;flex-direction:column;align-items:center;justify-content:center;color:var(--h5-text-placeholder);font-size:12px;overflow:hidden}.collection-images__add input{position:absolute;inset:0;opacity:0;width:100%;height:100%}.collection-images__add.inactive{opacity:.5}.collection-images__pending.norem{max-width:180px;min-height:80px;padding:8px;background:var(--h5-primary-opacity);border-radius:8px;color:var(--h5-text-secondary);overflow-wrap:anywhere;font-size:12px}.collection-images__pending button{margin:6px;border:0;background:transparent;color:var(--h5-primary)}
</style>
