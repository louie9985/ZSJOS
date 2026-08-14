<template>
  <el-form ref="formRef" :model="password" :rules="rules" :label-width="200">
    <el-form-item :label="t('profile.password.oldPassword')" prop="oldPassword">
      <InputPassword v-model="password.oldPassword" />
    </el-form-item>
    <el-form-item :label="t('profile.password.newPassword')" prop="newPassword">
      <InputPassword v-model="password.newPassword" strength />
    </el-form-item>
    <el-form-item :label="t('profile.password.confirmPassword')" prop="confirmPassword">
      <InputPassword v-model="password.confirmPassword" strength />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submit(formRef)">{{ t('common.save') }}</el-button>
      <el-button type="danger" @click="reset(formRef)">{{ t('common.reset') }}</el-button>
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'

import { InputPassword } from '@/components/InputPassword'
import { updateUserPassword } from '@/api/system/user/profile'

defineOptions({ name: 'ResetPwd' })

const { t } = useI18n()
const message = useMessage()
const formRef = ref<FormInstance>()
const password = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 表单校验
const equalToPassword = (_rule, value) => {
  if (password.newPassword !== value) {
    return Promise.reject(new Error(t('profile.password.diffPwd')))
  }
  return Promise.resolve()
}

const rules = reactive<FormRules>({
  oldPassword: [
    { required: true, message: t('profile.password.oldPwdMsg'), trigger: 'blur' },
    { min: 4, max: 100, message: '旧密码格式不正确', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('profile.password.newPwdMsg'), trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为 8-20 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '密码必须同时包含字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: t('profile.password.cfPwdMsg'), trigger: 'blur' },
    { required: true, validator: equalToPassword, trigger: 'blur' }
  ]
})

const submit = (formEl: FormInstance | undefined) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      await updateUserPassword(password.oldPassword, password.newPassword)
      message.success(t('common.updateSuccess'))
    }
  })
}

const reset = (formEl: FormInstance | undefined) => {
  if (!formEl) return
  formEl.resetFields()
}
</script>
