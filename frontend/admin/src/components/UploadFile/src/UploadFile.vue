<template>
  <ClipboardUploadActions
    v-if="!disabled"
    :disabled="disabled || fileList.length >= props.limit"
    :can-paste="fileList.length < props.limit"
    @files="handlePasteFiles"
  >
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :action="uploadUrl"
      :auto-upload="autoUpload"
      :before-upload="beforeUpload"
      :disabled="disabled"
      :drag="drag"
      :http-request="httpRequest"
      :limit="props.limit"
      :multiple="props.limit > 1"
      :on-error="excelUploadError"
      :on-exceed="handleExceed"
      :on-remove="handleRemove"
      :on-success="handleFileSuccess"
      :show-file-list="true"
      class="upload-file-uploader"
      name="file"
    >
      <el-button type="primary">
        <Icon icon="ep:upload-filled" />
        上传附件
      </el-button>
      <template v-if="isShowTip" #tip>
        <div style="font-size: 8px">
          格式为 <b style="color: #f56c6c">{{ fileType.join('/') }}</b> 的文件
        </div>
        <div style="font-size: 12px">可通过“上传剪贴板截图”直接读取截图</div>
      </template>
      <template #file="row">
        <div class="flex items-center gap-2">
          <AttachmentItem v-if="row.file.url" :url="row.file.url" :name="row.file.name" />
          <span v-else
            >{{ row.file.name }}（上传中 {{ Math.round(row.file.percentage || 0) }}%）</span
          >
          <el-button link type="danger" @click="removeFile(row.file)">删除</el-button>
        </div>
      </template>
    </el-upload>
  </ClipboardUploadActions>

  <!-- 上传操作禁用时 -->
  <div v-if="disabled" class="upload-file">
    <div v-for="(file, index) in fileList" :key="index" class="flex items-center file-list-item">
      <AttachmentItem v-if="file.url" :url="file.url" :name="file.name" />
    </div>
  </div>
</template>
<script lang="ts" setup>
import { propTypes } from '@/utils/propTypes'
import { getFileNameFromUrl } from '@/utils/file'
import {
  genFileId,
  type UploadInstance,
  type UploadProps,
  type UploadRawFile,
  type UploadUserFile
} from 'element-plus'
import { isString } from '@/utils/is'
import { useUpload } from '@/components/UploadFile/src/useUpload'
import { UploadFile } from 'element-plus/es/components/upload/src/upload'
import AttachmentItem from './AttachmentItem.vue'
import ClipboardUploadActions from './ClipboardUploadActions.vue'

defineOptions({ name: 'UploadFile' })

const message = useMessage() // 消息弹窗
const emit = defineEmits(['update:modelValue', 'uploading-change'])

const props = defineProps({
  modelValue: propTypes.oneOfType<string | string[]>([String, Array<String>]).isRequired,
  fileType: propTypes.array.def(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'pdf']), // 文件类型, 例如['png', 'jpg', 'jpeg']
  limit: propTypes.number.def(5), // 数量限制
  autoUpload: propTypes.bool.def(true), // 自动上传
  drag: propTypes.bool.def(false), // 拖拽上传
  isShowTip: propTypes.bool.def(true), // 是否显示提示
  disabled: propTypes.bool.def(false), // 是否禁用上传组件 ==> 非必传（默认为 false）
  directory: propTypes.string.def(undefined) // 上传目录 ==> 非必传（默认为 undefined）
})

// ========== 上传相关 ==========
const fileList = ref<UploadUserFile[]>([])
let lastEmitted: string | undefined

const { uploadUrl, httpRequest } = useUpload(props.directory)
const uploadRef = ref<UploadInstance>()

const handlePasteFiles = (files: File[]) => {
  files
    .slice(0, props.limit - fileList.value.length)
    .forEach((file) =>
      uploadRef.value?.handleStart(Object.assign(file, { uid: genFileId() }) as UploadRawFile)
    )
  if (props.autoUpload) uploadRef.value?.submit()
}

// 文件上传之前判断
const beforeUpload: UploadProps['beforeUpload'] = (file: UploadRawFile) => {
  if (fileList.value.length > props.limit) {
    message.error(`上传文件数量不能超过${props.limit}个!`)
    return false
  }
  let fileExtension = ''
  if (file.name.lastIndexOf('.') > -1) {
    fileExtension = file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
  }
  const isImg =
    props.fileType.length === 0 ||
    props.fileType.some((type: string) => type.toLowerCase() === fileExtension)
  if (!isImg) {
    message.error(`文件格式不正确, 请上传${props.fileType.join('/')}格式!`)
    return false
  }
  message.success('正在上传文件，请稍候...')
  return true
}
// 按 uid 更新当前文件，避免同名文件和并发成功/失败相互覆盖。
const handleFileSuccess: UploadProps['onSuccess'] = (res, file) => {
  const url = res?.data
  if ((res?.code != null && res.code !== 0) || typeof url !== 'string' || !url.trim()) {
    fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
    message.error(res?.msg || '上传失败：未返回有效文件地址，请重试')
    return
  }
  const target = fileList.value.find((item) => item.uid === file.uid)
  if (!target) return
  target.url = url
  target.status = 'success'
  message.success('上传成功')
  emitUpdateModelValue()
}
// 文件数超出提示
const handleExceed: UploadProps['onExceed'] = (): void => {
  message.error(`上传文件数量不能超过${props.limit}个!`)
}
const excelUploadError: UploadProps['onError'] = () => {
  message.error('文件上传失败，请重试')
}
const handleRemove: UploadProps['onRemove'] = () => emitUpdateModelValue()
const removeFile = (file: UploadFile) => uploadRef.value?.handleRemove(file)

watch(
  () => fileList.value.some((file) => file.status === 'ready' || file.status === 'uploading'),
  (value) => emit('uploading-change', value)
)
onBeforeUnmount(() => {
  uploadRef.value?.abort()
  emit('uploading-change', false)
})

// 监听模型绑定值变动
watch(
  () => props.modelValue,
  (val: string | string[]) => {
    // 自身回写不重建列表，否则会丢失仍在上传的文件和原始文件名。
    if (JSON.stringify(val) === lastEmitted) return
    lastEmitted = undefined
    uploadRef.value?.abort()
    const urls = isString(val) ? val.split(',') : val || []
    fileList.value = urls
      .filter((url): url is string => typeof url === 'string' && !!url.trim())
      .map((url) => ({ name: getFileNameFromUrl(url), url, status: 'success' }))
  },
  { immediate: true, deep: true }
)
// 发送文件链接列表更新
const emitUpdateModelValue = () => {
  // 情况1：数组结果
  let result: string | string[] = fileList.value
    .filter((file) => file.status === 'success' && file.url)
    .map((file) => file.url!)
  // 情况2：逗号分隔的字符串
  if (props.limit === 1 || isString(props.modelValue)) {
    result = result.join(',')
  }
  lastEmitted = JSON.stringify(result)
  emit('update:modelValue', result)
}
</script>
<style lang="scss" scoped>
.upload-file-uploader {
  margin-bottom: 5px;
}

:deep(.upload-file-list .el-upload-list__item) {
  position: relative;
  margin-bottom: 10px;
  line-height: 2;
  border: 1px solid #e4e7ed;
}

:deep(.el-upload-list__item-file-name) {
  max-width: 250px;
}

:deep(.upload-file-list .ele-upload-list__item-content) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: inherit;
}

:deep(.ele-upload-list__item-content-action .el-link) {
  margin-right: 10px;
}

.file-list-item {
  border: 1px dashed var(--el-border-color-darker);
  border-radius: 8px;
}
</style>
