<template>
  <el-table :data="socialUsers" :show-header="false">
    <el-table-column fixed="left" title="序号" type="seq" width="60" />
    <el-table-column align="left" label="社交平台" width="120">
      <template #default="{ row }">
        <img :src="row.img" alt="" class="h-5 align-middle" />
        <p class="mr-5">{{ row.title }}</p>
      </template>
    </el-table-column>
    <el-table-column align="center" label="操作">
      <template #default="{ row }">
        <template v-if="row.openid">
          已绑定
          <el-button link class="mr-5" type="primary" @click="unbind(row)">(解绑)</el-button>
        </template>
        <template v-else>
          未绑定
          <el-button link class="mr-5" type="primary" @click="bind(row)">(绑定)</el-button>
        </template>
      </template>
    </el-table-column>
  </el-table>
</template>
<script lang="ts" setup>
import { SystemUserSocialTypeEnum } from '@/utils/constants'
import { getBindSocialUserList } from '@/api/system/social/user'
import { socialAuthRedirect, socialBind, socialUnbind } from '@/api/system/user/socialUser'

defineOptions({ name: 'UserSocial' })
defineProps<{
  activeName: string
}>()
const message = useMessage()
const socialUsers = ref<any[]>([])
const VISIBLE_SOCIAL_TYPES = new Set([SystemUserSocialTypeEnum.WECHAT_ENTERPRISE.type])

const initSocial = async () => {
  socialUsers.value = [] // 重置避免无限增长
  // 获取已绑定的社交用户列表
  const bindSocialUserList = await getBindSocialUserList()
  // 检查该社交平台是否已绑定
  for (const i in SystemUserSocialTypeEnum) {
    const socialUser = { ...SystemUserSocialTypeEnum[i] }
    if (!VISIBLE_SOCIAL_TYPES.has(socialUser.type)) continue
    socialUsers.value.push(socialUser)
    if (bindSocialUserList && bindSocialUserList.length > 0) {
      for (const bindUser of bindSocialUserList) {
        if (socialUser.type === bindUser.type) {
          socialUser.openid = bindUser.openid
          break
        }
      }
    }
  }
}
const route = useRoute()
const emit = defineEmits<{
  (e: 'update:activeName', v: string): void
}>()
const bindSocial = () => {
  // 社交绑定
  const type = route.query.type
  const code = route.query.code
  const state = route.query.state
  if (!code) {
    return
  }
  socialBind(type, code, state).then(() => {
    message.success('绑定成功')
    emit('update:activeName', 'userSocial')
  })
}

const bind = (row) => {
  const redirectUri = location.origin + `/user/profile?type=${row.type}`
  // 进行跳转
  socialAuthRedirect(row.type, redirectUri).then((res) => {
    window.location.href = res
  })
}
const unbind = async (row) => {
  const res = await socialUnbind(row.type, row.openid)
  if (res) {
    row.openid = undefined
  }
  message.success('解绑成功')
}

onMounted(async () => {
  await initSocial()
})

watch(
  () => route,
  () => {
    bindSocial()
  },
  {
    immediate: true
  }
)
</script>
