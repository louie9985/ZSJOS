<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getProfile, updateProfile, type UserProfile } from '@/api/profile'

defineOptions({ name: 'ProfileEdit' })

const router = useRouter()
const route = useRoute()
const completing = computed(() => route.query.complete === '1')
const loading = ref(true)
const submitting = ref(false)
const loadFailed = ref(false)
const form = ref<Omit<UserProfile, 'nickname'> & { nickname: string }>({ name: '', nickname: '', mobile: '', email: '', avatar: '', sex: 0 })

async function loadProfile() {
  loading.value = true
  loadFailed.value = false
  try {
    const profile = await getProfile()
    form.value = { ...profile, nickname: profile.nickname ?? '' }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(loadProfile)

async function handleSave() {
  if (loading.value || loadFailed.value || submitting.value) return
  if (!form.value.name?.trim()) { showToast('请输入姓名'); return }
  if (!form.value.nickname?.trim()) { showToast('请输入昵称'); return }
  submitting.value = true
  try {
    await updateProfile({
      name: form.value.name.trim(),
      nickname: form.value.nickname.trim(),
      email: form.value.email?.trim() || undefined,
      avatar: form.value.avatar?.trim() || undefined,
      sex: form.value.sex
    })
    showSuccessToast('保存成功')
    if (completing.value) {
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
      router.replace(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/home')
    } else {
      router.back()
    }
  } catch { /* */ } finally { submitting.value = false }
}
</script>

<template>
  <div class="page-container my-subpage-page profile-form-page">
    <van-nav-bar class="my-subpage__nav" :title="completing ? '完善个人信息' : '个人资料'" :left-arrow="!completing" @click-left="!completing && $router.back()" />
    <van-empty v-if="loadFailed" description="个人资料加载失败">
      <van-button type="primary" @click="loadProfile">重新加载</van-button>
    </van-empty>
    <template v-else>
    <van-skeleton :loading="loading" :row="4" style="padding: 16px;">
      <div class="card profile-form-card">
        <van-field v-model="form.name" label="姓名" placeholder="请输入姓名" maxlength="100" required clearable />
        <p class="name-hint">请使用真实姓名，方便后续返现财务审核</p>
        <van-field v-model="form.nickname" label="昵称" placeholder="用于排行榜展示" maxlength="100" required clearable />
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
        <van-button type="primary" block round :loading="submitting" @click="handleSave">{{ completing ? '保存并进入' : '保存' }}</van-button>
      </div>
    </van-skeleton>
    </template>
  </div>
</template>

<style scoped>
.name-hint {
  margin: 0;
  padding: 0 16px 10px;
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.5;
}
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
