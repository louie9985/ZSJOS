<template>
  <Dialog v-model="dialogVisible" title="默认员工头像" width="520px">
    <el-form v-loading="loading" label-width="110px">
      <el-form-item label="默认头像">
        <UploadImg
          v-model="avatar"
          width="120px"
          height="120px"
          borderradius="50%"
          directory="system/user/avatar"
          :file-size="2"
          :is-show-tip="false"
          :disabled="!canUpdate"
        />
      </el-form-item>
      <el-alert
        :closable="false"
        type="info"
        title="仅在员工未设置个人头像时使用；清空后回退显示昵称首字。"
      />
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取 消</el-button>
      <el-button v-if="canUpdate" type="primary" :loading="saving" @click="submitForm">
        保 存
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ConfigApi from '@/api/infra/config'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'DefaultUserAvatarForm' })

const message = useMessage()
const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const avatar = ref('')
const canUpdate = computed(() => checkPermi(['infra:config:update']))

const open = async () => {
  dialogVisible.value = true
  loading.value = true
  try {
    avatar.value = (await ConfigApi.getDefaultUserAvatar()) || ''
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  saving.value = true
  try {
    await ConfigApi.updateDefaultUserAvatar(avatar.value)
    message.success('默认员工头像已更新')
    dialogVisible.value = false
    emit('success', avatar.value)
  } finally {
    saving.value = false
  }
}
</script>
