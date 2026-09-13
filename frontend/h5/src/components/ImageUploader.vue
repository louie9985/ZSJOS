<script setup lang="ts">
import { computed } from 'vue'
import { showToast } from 'vant'
import { useUpload } from '@/composables/useUpload'
import type { UploadResult } from '@/api/lead'

/**
 * 图片上传组件
 * 选择后立即上传，展示状态（上传中/完成/失败）
 */

const props = withDefaults(defineProps<{
  maxCount?: number
  accept?: string
}>(), {
  maxCount: 9,
  accept: 'image/jpeg,image/png,image/webp'
})

const { fileList, uploading, addFile, removeFile, retryFile, reset, getUploadedFiles } = useUpload(props.maxCount)

const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])
const maxFileSize = 10 * 1024 * 1024

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return

  const availableCount = props.maxCount - fileList.value.length
  const selectedFiles = Array.from(files)
  if (selectedFiles.length > availableCount) {
    showToast(`最多上传 ${props.maxCount} 张图片`)
  }

  for (const file of selectedFiles.slice(0, availableCount)) {
    if (!allowedTypes.has(file.type)) {
      showToast(`${file.name} 仅支持 JPG、PNG、WebP`)
      continue
    }
    if (file.size > maxFileSize) {
      showToast(`${file.name} 不能超过 10MB`)
      continue
    }
    addFile(file)
  }
  // 清空 input 以便重复选择相同文件
  input.value = ''
}

const canAdd = computed(() => fileList.value.length < props.maxCount)

// 暴露给父组件获取已上传文件
defineExpose({
  getUploadedIds: () => fileList.value.filter(f => f.status === 'done' && f.result).map(f => f.result!.infraFileId),
  getUploadedFiles: (): UploadResult[] => getUploadedFiles(),
  hasError: () => fileList.value.some(f => f.status === 'error'),
  isUploading: () => uploading.value,
  reset
})
</script>

<template>
  <div class="image-uploader">
    <div class="image-uploader__list">
      <!-- 已选图片 -->
      <div
        v-for="item in fileList"
        :key="item.id"
        class="image-uploader__item"
      >
        <img :src="item.url" class="image-uploader__img" alt="" />

        <div v-if="item.status === 'queued'" class="image-uploader__mask" role="status">
          <span class="image-uploader__status-text">等待上传</span>
        </div>

        <div v-else-if="item.status === 'uploading'" class="image-uploader__mask" role="status">
          <van-circle
            :current-rate="item.progress"
            :rate="item.progress"
            :speed="100"
            size="38px"
            stroke-width="80"
            color="#fff"
            layer-color="rgba(255, 255, 255, 0.28)"
            :text="`${item.progress}%`"
          />
        </div>

        <div v-else-if="item.status === 'processing'" class="image-uploader__mask" role="status">
          <van-loading size="20" color="#fff" />
          <span class="image-uploader__status-text">处理中</span>
        </div>

        <!-- 失败遮罩 -->
        <div v-if="item.status === 'error'" class="image-uploader__mask image-uploader__mask--error" @click="retryFile(item.id)">
          <van-icon name="replay" size="20" color="#fff" />
          <span class="image-uploader__retry-text">重试</span>
        </div>

        <div v-if="item.status === 'done'" class="image-uploader__done">
          <van-icon name="success" size="12" color="#fff" />
        </div>

        <!-- 删除按钮 -->
        <button
          type="button"
          class="image-uploader__delete"
          :aria-label="item.status === 'done' || item.status === 'error' ? '删除图片' : '取消上传并删除图片'"
          title="删除图片"
          @click.stop="removeFile(item.id)"
        >
          <van-icon name="cross" size="14" />
        </button>
        <div v-if="item.status === 'error' && item.error" class="image-uploader__error-text">
          {{ item.error }}
        </div>
      </div>

      <!-- 添加按钮 -->
      <label v-if="canAdd" class="image-uploader__add">
        <van-icon name="photograph" size="24" color="var(--h5-text-placeholder)" />
        <span class="image-uploader__add-text">{{ fileList.length }}/{{ maxCount }}</span>
        <input
          type="file"
          :accept="accept"
          multiple
          class="image-uploader__input"
          @change="onFileChange"
        />
      </label>
    </div>
  </div>
</template>

<style scoped>
.image-uploader__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-uploader__item {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.image-uploader__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-uploader__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.image-uploader__mask :deep(.van-circle__text) {
  color: #fff;
  font-size: 10px;
}
.image-uploader__status-text {
  margin-top: 4px;
  color: #fff;
  font-size: 10px;
}
.image-uploader__mask--error {
  cursor: pointer;
}
.image-uploader__retry-text {
  font-size: 10px;
  color: #fff;
  margin-top: 2px;
}

.image-uploader__delete {
  position: absolute;
  top: 2px;
  right: 2px;
  display: flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  color: rgba(255, 255, 255, 0.95);
  background: rgba(0, 0, 0, 0.4);
  border-radius: 50%;
  z-index: 2;
}
.image-uploader__done {
  position: absolute;
  left: 4px;
  bottom: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--h5-success);
}
.image-uploader__error-text {
  position: absolute;
  left: 2px;
  right: 2px;
  bottom: 2px;
  overflow: hidden;
  color: #fff;
  font-size: 9px;
  line-height: 12px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-uploader__add {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  border: 1px dashed var(--h5-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  background: var(--h5-glass-surface-strong);
}
.image-uploader__add-text {
  font-size: 10px;
  color: var(--h5-text-placeholder);
}
.image-uploader__input {
  display: none;
}
</style>
