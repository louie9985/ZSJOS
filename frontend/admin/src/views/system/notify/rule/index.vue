<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" :inline="true" class="-mb-15px">
      <el-form-item label="规则名称" prop="name">
        <el-input
          v-model="query.name"
          clearable
          placeholder="请输入规则名称"
          class="!w-220px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="业务场景" prop="sceneCode">
        <el-select v-model="query.sceneCode" clearable placeholder="全部场景" class="!w-220px">
          <el-option
            v-for="scene in scenes"
            :key="scene.code"
            :label="scene.name"
            :value="scene.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="search"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['system:notify-rule:create']"
          @click="openForm()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="规则名称" prop="name" min-width="160" />
      <el-table-column label="业务场景" min-width="160">
        <template #default="scope">{{ sceneName(scope.row.sceneCode) }}</template>
      </el-table-column>
      <el-table-column label="通知渠道" width="110">
        <template #default="scope">{{ channelName(scope.row.channelCode) }}</template>
      </el-table-column>
      <el-table-column label="模板" min-width="160">
        <template #default="scope">{{ templateName(scope.row.templateId) }}</template>
      </el-table-column>
      <el-table-column label="事件角色" min-width="180">
        <template #default="scope">{{ roleNames(scope.row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="指定用户" min-width="180">
        <template #default="scope">{{ userNames(scope.row.specifiedUserIds) || '-' }}</template>
      </el-table-column>
      <el-table-column label="点击动作" width="120">
        <template #default="scope">{{ actionName(scope.row.actionType) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="90">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.status === CommonStatusEnum.ENABLE"
            v-hasPermi="['system:notify-rule:update']"
            @change="toggleStatus(scope.row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['system:notify-rule:update']"
            @click="openForm(scope.row.id)"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            v-hasPermi="['system:notify-rule:delete']"
            @click="remove(scope.row.id)"
            >删除</el-button
          >
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无业务通知规则" /></template>
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑业务通知规则' : '新增业务通知规则'">
    <el-form ref="formRef" v-loading="formLoading" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="规则名称" prop="name"
        ><el-input v-model="form.name" maxlength="64"
      /></el-form-item>
      <el-form-item label="业务场景" prop="sceneCode">
        <el-select v-model="form.sceneCode" class="!w-100%" @change="sceneChanged">
          <el-option
            v-for="scene in scenes"
            :key="scene.code"
            :label="scene.name"
            :value="scene.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="通知渠道" prop="channelCode">
        <el-select v-model="form.channelCode" class="!w-100%" @change="channelChanged">
          <el-option v-for="item in channels" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="消息模板" prop="templateId">
        <el-select v-model="form.templateId" class="!w-100%" placeholder="请选择同场景模板">
          <el-option
            v-for="template in sceneTemplates"
            :key="template.id"
            :label="template.name"
            :value="template.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="事件角色">
        <el-checkbox-group v-model="form.recipientRoles">
          <el-checkbox
            v-for="role in currentScene?.recipientRoles || []"
            :key="role.code"
            :value="role.code"
            >{{ role.name }}</el-checkbox
          >
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="指定用户">
        <el-select
          v-model="form.specifiedUserIds"
          multiple
          filterable
          collapse-tags
          class="!w-100%"
          placeholder="可选；不会自动授予客资权限"
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="`${user.nickname} (${user.username})`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="点击动作" prop="actionType">
        <el-radio-group v-model="form.actionType">
          <el-radio-button
            v-for="action in currentScene?.allowedActions || []"
            :key="action"
            :value="action"
            >{{ actionName(action) }}</el-radio-button
          >
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-switch
          v-model="form.status"
          :active-value="CommonStatusEnum.ENABLE"
          :inactive-value="CommonStatusEnum.DISABLE"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submit">确定</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as RuleApi from '@/api/system/notify/rule'
import * as TemplateApi from '@/api/system/notify/template'
import * as UserApi from '@/api/system/user'
import { CommonStatusEnum } from '@/utils/constants'

defineOptions({ name: 'SystemNotifyRule' })
const message = useMessage()
const loading = ref(false)
const error = ref('')
const list = ref<RuleApi.NotifyRuleVO[]>([])
const total = ref(0)
const scenes = ref<RuleApi.NotifySceneVO[]>([])
const templates = ref<TemplateApi.NotifyTemplateSimpleVO[]>([])
const users = ref<UserApi.UserVO[]>([])
const queryFormRef = ref()
const query = reactive({ pageNo: 1, pageSize: 10, name: undefined, sceneCode: undefined })

const sceneName = (code: string) => scenes.value.find((scene) => scene.code === code)?.name || code
const templateName = (id?: number) =>
  templates.value.find((template) => template.id === id)?.name || `#${id}`
const actionName = (action: string) =>
  ({ none: '无操作', message_detail: '消息详情', business_detail: '客资详情' })[action] || action
const channels = [
  { value: 'in_app', label: '站内信（含实时提醒）' },
  { value: 'wecom', label: '企业微信（待配置）' }, { value: 'sms', label: '短信（待配置）' }
]
const channelName = (code?: string) => channels.find((item) => item.value === code)?.label || '站内信'
const roleNames = (rule: RuleApi.NotifyRuleVO) => {
  const scene = scenes.value.find((item) => item.code === rule.sceneCode)
  return rule.recipientRoles
    .map((code) => scene?.recipientRoles.find((role) => role.code === code)?.name || code)
    .join('、')
}
const userNames = (ids: number[]) =>
  ids.map((id) => users.value.find((user) => user.id === id)?.nickname || `#${id}`).join('、')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await RuleApi.getNotifyRulePage(query)
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    error.value = e?.msg || e?.message || '业务通知规则加载失败'
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  queryFormRef.value?.resetFields()
  search()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const emptyForm = (): RuleApi.NotifyRuleVO => ({
  name: '',
  sceneCode: '',
  channelCode: 'in_app',
  templateId: undefined,
  recipientRoles: [],
  specifiedUserIds: [],
  actionType: 'message_detail',
  status: CommonStatusEnum.ENABLE
})
const form = ref<RuleApi.NotifyRuleVO>(emptyForm())
const currentScene = computed(() =>
  scenes.value.find((scene) => scene.code === form.value.sceneCode)
)
const sceneTemplates = computed(() =>
  templates.value.filter((template) => template.sceneCode === form.value.sceneCode
    && (!template.channelCode || template.channelCode === form.value.channelCode
      || (form.value.channelCode === 'websocket' && template.channelCode === 'in_app')))
)
const rules = {
  name: [{ required: true, message: '规则名称不能为空' }],
  sceneCode: [{ required: true, message: '业务场景不能为空' }],
  channelCode: [{ required: true, message: '通知渠道不能为空' }],
  templateId: [{ required: true, message: '消息模板不能为空' }],
  actionType: [{ required: true, message: '点击动作不能为空' }]
}
const sceneChanged = () => {
  form.value.templateId = undefined
  form.value.recipientRoles = []
  form.value.actionType = currentScene.value?.allowedActions.includes('message_detail')
    ? 'message_detail'
    : 'none'
}
const channelChanged = () => { form.value.templateId = undefined }
const openForm = async (id?: number) => {
  dialogVisible.value = true
  form.value = emptyForm()
  if (id) {
    formLoading.value = true
    try {
      form.value = await RuleApi.getNotifyRule(id)
    } finally {
      formLoading.value = false
    }
  }
}
const submit = async () => {
  if (!(await formRef.value?.validate())) return
  if (!form.value.recipientRoles.length && !form.value.specifiedUserIds.length) {
    message.warning('至少选择一个事件角色或指定用户')
    return
  }
  formLoading.value = true
  try {
    form.value.id
      ? await RuleApi.updateNotifyRule(form.value)
      : await RuleApi.createNotifyRule(form.value)
    message.success(form.value.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await load()
  } finally {
    formLoading.value = false
  }
}
const toggleStatus = async (row: RuleApi.NotifyRuleVO, enabled: boolean | string | number) => {
  await RuleApi.updateNotifyRuleStatus(
    row.id!,
    enabled ? CommonStatusEnum.ENABLE : CommonStatusEnum.DISABLE
  )
  message.success('状态已更新')
  await load()
}
const remove = async (id: number) => {
  try {
    await message.delConfirm()
    await RuleApi.deleteNotifyRule(id)
    message.success('删除成功')
    await load()
  } catch {}
}

onMounted(async () => {
  const [sceneResult, templateResult, userResult] = await Promise.allSettled([
    RuleApi.getNotifySceneList(),
    TemplateApi.getSimpleNotifyTemplateList(),
    UserApi.getSimpleUserList()
  ])
  if (sceneResult.status === 'fulfilled') scenes.value = sceneResult.value
  if (templateResult.status === 'fulfilled') templates.value = templateResult.value
  if (userResult.status === 'fulfilled') users.value = userResult.value
  await load()
})
</script>
