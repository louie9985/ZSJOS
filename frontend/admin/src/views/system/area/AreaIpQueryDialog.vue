<template>
  <Dialog v-model="dialogVisible" title="IP 查询">
    <el-form ref="formRef" v-loading="loading" :model="formData" :rules="rules" label-width="80px">
      <el-form-item label="IP" prop="ip"
        ><el-input v-model="formData.ip" placeholder="请输入 IP 地址"
      /></el-form-item>
      <el-form-item label="地址"
        ><el-input v-model="formData.result" readonly placeholder="查询结果"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="query">查询</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as AreaApi from '@/api/system/area'

defineOptions({ name: 'SystemAreaIpQueryDialog' })
const dialogVisible = ref(false)
const loading = ref(false)
const formRef = ref()
const formData = reactive({ ip: '', result: '' })
const rules = { ip: [{ required: true, message: 'IP 地址不能为空', trigger: 'blur' }] }
const open = () => {
  formData.ip = ''
  formData.result = ''
  dialogVisible.value = true
}
defineExpose({ open })
const query = async () => {
  if (!(await formRef.value?.validate())) return
  loading.value = true
  try {
    formData.result = await AreaApi.getAreaByIp(formData.ip.trim())
  } finally {
    loading.value = false
  }
}
</script>
