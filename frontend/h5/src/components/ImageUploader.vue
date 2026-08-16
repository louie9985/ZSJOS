<script setup lang="ts">
import { computed } from 'vue'
import { showToast } from 'vant'
import { useUpload, type UploadFile } from '@/composables/useUpload'

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

const { fileList, uploading, addFile, removeFile, retryFile } = useUpload(props.maxCount)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return

  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    // 校验大小（10MB）
    if (file.size > 10 * 1024 * 1024) {
      showToast(`${file.name} 超过 10MB 限制`)
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
  hasError: () => fileList.value.some(f => f.status === 'error'),
  isUploading: () => uploading.value,
  reset: () => { fileList.value = [] }
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

        <!-- 上传中遮罩 -->
        <div v-if="item.status === 'uploading'" class="image-uploader__mask">
          <van-loading size="20" color="#fff" />
        </div>

        <!-- 失败遮罩 -->
        <div v-if="item.status === 'error'" class="image-uploader__mask image-uploader__mask--error" @click="retryFile(item.id)">
          <van-icon name="replay" size="20" color="#fff" />
          <span class="image-uploader__retry-text">重试</span>
        </div>

        <!-- 删除按钮 -->
        <van-icon
          name="clear"
          class="image-uploader__delete"
          size="18"
          @click.stop="removeFile(item.id)"
        />
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
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.4);
  border-radius: 50%;
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
  background: var(--h5-card-bg);
}
.image-uploader__add-text {
  font-size: 10px;
  color: var(--h5-text-placeholder);
}
.image-uploader__input {
  display: none;
}
</style>
