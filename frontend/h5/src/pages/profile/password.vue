<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { updatePassword } from '@/api/profile'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ProfilePassword' })

const router = useRouter()
const userStore = useUserStore()

const form = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)

async function handleSubmit() {
  if (!form.value.oldPassword) { showToast('请输入当前密码'); return }
  if (!form.value.newPassword) { showToast('请输入新密码'); return }
  if (form.value.newPassword.length < 8 || form.value.newPassword.length > 20) { showToast('新密码需要 8-20 位'); return }
  if (!/[a-zA-Z]/.test(form.value.newPassword) || !/\d/.test(form.value.newPassword)) { showToast('新密码需同时包含字母和数字'); return }
  if (form.value.newPassword !== form.value.confirmPassword) { showToast('两次密码不一致'); return }

  submitting.value = true
  try {
    await updatePassword({ oldPassword: form.value.oldPassword, newPassword: form.value.newPassword })
    showSuccessToast('密码修改成功，请重新登录')
    userStore.logout()
    router.replace('/login')
  } catch { /* */ } finally { submitting.value = false }
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="修改密码" left-arrow @click-left="$router.back()" />
    <div class="card">
      <van-field v-model="form.oldPassword" type="password" label="当前密码" placeholder="请输入当前密码" required />
      <van-field v-model="form.newPassword" type="password" label="新密码" placeholder="8-20位，包含字母和数字" required />
      <van-field v-model="form.confirmPassword" type="password" label="确认密码" placeholder="再次输入新密码" required />
    </div>
    <div style="padding: 24px 16px;">
      <van-button type="primary" block round :loading="submitting" @click="handleSubmit">确认修改</van-button>
    </div>
  </div>
</template>
