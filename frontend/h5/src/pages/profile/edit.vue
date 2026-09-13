<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getProfile, updateProfile, type UserProfile } from '@/api/profile'

defineOptions({ name: 'ProfileEdit' })

const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const form = ref<UserProfile>({ nickname: '', mobile: '', email: '', avatar: '', sex: 0 })

onMounted(async () => {
  try {
    form.value = await getProfile()
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  if (!form.value.nickname?.trim()) { showToast('请输入昵称'); return }
  submitting.value = true
  try {
    await updateProfile({
      nickname: form.value.nickname.trim(),
      email: form.value.email?.trim() || undefined,
      avatar: form.value.avatar?.trim() || undefined,
      sex: form.value.sex
    })
    showSuccessToast('保存成功')
    router.back()
  } catch { /* */ } finally { submitting.value = false }
}
</script>

<template>
  <div class="page-container my-subpage-page profile-form-page">
    <van-nav-bar class="my-subpage__nav" title="个人资料" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="4" style="padding: 16px;">
      <div class="card profile-form-card">
        <van-field v-model="form.avatar" label="头像地址" placeholder="可填写图片地址" clearable />
        <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" required clearable />
        <van-field v-model="form.mobile" label="手机号" type="tel" readonly />
        <van-field v-model="form.email" label="邮箱" type="email" placeholder="请输入邮箱" clearable />
        <van-field label="性别">
          <template #input>
            <van-radio-group v-model="form.sex" class="profile-sex-group" direction="horizontal">
              <van-radio :name="0">未知</van-radio>
              <van-radio :name="1">男</van-radio>
              <van-radio :name="2">女</van-radio>
            </van-radio-group>
          </template>
        </van-field>
      </div>
      <div style="padding: 24px 16px;">
        <van-button type="primary" block round :loading="submitting" @click="handleSave">保存</van-button>
      </div>
    </van-skeleton>
  </div>
</template>

<style scoped>
.profile-form-page .profile-form-card {
  background: var(--h5-content-surface);
}

.profile-sex-group {
  width: 100%;
  flex-wrap: nowrap;
  gap: 12px;
}

.profile-sex-group :deep(.van-radio--horizontal) {
  margin-right: 0;
}
</style>
