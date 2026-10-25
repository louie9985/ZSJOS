<template>
  <div v-loading="loading" style="max-width: 560px; min-height: 100px">
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="mb-16px">
      <template #default
        ><el-button link :disabled="busy" @click="load">{{
          options ? '刷新选项' : '重试'
        }}</el-button></template
      >
    </el-alert>
    <el-form
      v-if="options"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="busy"
      @submit.prevent="submit"
    >
      <el-form-item label="学员姓名" prop="name"
        ><el-input v-model="form.name" maxlength="100"
      /></el-form-item>
      <el-form-item label="手机号" prop="mobile"
        ><el-input v-model="form.mobile" maxlength="32" @input="resetCheck"
      /></el-form-item>
      <el-form-item label="微信号" prop="wechatId"
        ><el-input v-model="form.wechatId" maxlength="64" @input="resetCheck"
      /></el-form-item>
      <el-form-item label="课程 SKU（选填）"
        ><el-select
          v-model="form.skuRef"
          clearable
          filterable
          placeholder="未填写课程"
          style="width: 100%"
        >
          <el-option
            v-for="c in options.courses"
            :key="c.skuRef"
            :label="c.name"
            :value="c.skuRef"
          /> </el-select
      ></el-form-item>
      <template v-if="type === 'media'">
        <el-form-item label="责任编导" prop="directorUserId"
          ><el-select
            v-model="form.directorUserId"
            filterable
            placeholder="请选择责任编导"
            style="width: 100%"
          >
            <el-option
              v-for="d in options.directors"
              :key="d.id"
              :label="d.name"
              :value="d.id"
            /> </el-select
        ></el-form-item>
        <el-form-item
          ><el-checkbox v-model="form.teachingDelivery"
            >同时建立教学服务，加入待分班</el-checkbox
          ></el-form-item
        >
      </template>
      <template v-else>
        <el-form-item label="班级" prop="classId"
          ><el-select
            v-model="form.classId"
            filterable
            placeholder="请选择班级或待分班"
            style="width: 100%"
          >
            <el-option
              v-for="c in options.classes"
              :key="c.id"
              :value="c.id"
              :label="`${c.className}${c.homeroomUserName ? ' · ' + c.homeroomUserName : ''}`"
            /> </el-select
        ></el-form-item>
        <p
          >教学负责人：{{
            selectedClass?.homeroomUserName || '未分配'
          }}；正式班由班主任接收后开始首联。</p
        >
      </template>
      <el-alert
        v-if="checked?.status === 'reuse'"
        type="info"
        :closable="false"
        show-icon
        :title="`已有人员：${checked.name || '姓名未提供'}（${checked.personNo || '编号未提供'}）`"
      >
        <p>当前可见服务 {{ checked.services.length }} 条；本次另建服务，原资料及进度保持原值。</p>
        <div v-for="s in checked.services" :key="s.serviceRelationId"
          >{{ s.studentName || '姓名未记录' }} · {{ s.statusLabel }}</div
        >
        <el-checkbox v-model="confirmReuse">确认复用此人员并新增服务</el-checkbox>
      </el-alert>
      <el-form-item class="mt-16px"
        ><el-button type="primary" :loading="busy" native-type="submit"
          >添加学员</el-button
        ></el-form-item
      >
    </el-form>
  </div>
</template>
<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import * as Api from '@/api/zsjos/studentEntry'
import { generateUUID } from '@/utils'
const props = defineProps<{ type: Api.EntryType; classId?: number }>()
const emit = defineEmits<{ saved: [result: Api.EntryResult]; busy: [value: boolean] }>()
const formRef = ref<FormInstance>()
const options = ref<Api.EntryOptions>(),
  checked = ref<Api.EntryCheck>()
const loading = ref(true),
  busy = ref(false),
  error = ref(''),
  confirmReuse = ref(false)
const form = reactive({
  name: '',
  mobile: '',
  wechatId: '',
  skuRef: undefined as string | undefined,
  directorUserId: undefined as number | undefined,
  teachingDelivery: false,
  classId: props.classId
})
const selectedClass = computed(() => options.value?.classes.find((c) => c.id === form.classId))
let active = true,
  generation = 0
onBeforeUnmount(() => {
  active = false
  generation++
})
let command: { intent: string; payload: Api.EntryRequest } | undefined
const resetCheck = () => {
  checked.value = undefined
  confirmReuse.value = false
}
const contactValidator = (_rule: unknown, _value: unknown, callback: (e?: Error) => void) =>
  callback(
    form.mobile.trim() || form.wechatId.trim() ? undefined : new Error('手机号或微信号至少填写一项')
  )
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请填写学员姓名' }],
  mobile: [{ validator: contactValidator }],
  wechatId: [{ validator: contactValidator }],
  directorUserId: [{ required: true, message: '请选择责任编导' }],
  classId: [{ required: true, message: '请选择班级或待分班' }]
}
const load = async () => {
  const version = ++generation
  loading.value = true
  error.value = ''
  try {
    const result = await Api.options()
    if (active && version === generation) options.value = result
  } catch (e: any) {
    if (active && version === generation) error.value = e?.msg || e?.message || '录入选项加载失败'
  } finally {
    if (active && version === generation) loading.value = false
  }
}
const submit = async () => {
  if (busy.value || !options.value || !(await formRef.value?.validate().catch(() => false))) return
  if (busy.value) return
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    const contacts = {
      mobile: form.mobile.trim() || undefined,
      wechatId: form.wechatId.trim() || undefined
    }
    const intentValues = {
      ...contacts, name: form.name.trim(), entryType: props.type, skuRef: form.skuRef || undefined,
      directorUserId: props.type === 'media' ? form.directorUserId : undefined,
      teachingDelivery: props.type === 'teaching' || form.teachingDelivery,
      classId: props.type === 'teaching' ? form.classId : undefined
    }
    const intent = JSON.stringify(intentValues)
    // Replay the exact attempted command before contact classification can change after a commit.
    if (command?.intent === intent) {
      emit('saved', await Api.create(command.payload))
      return
    }
    const result = await Api.check(contacts)
    const previous = checked.value
    checked.value = result
    if (result.status === 'occupied' || result.status === 'conflict') {
      error.value = '联系方式已被占用或存在冲突，请由有权人员核实'
      return
    }
    if (
      result.status === 'reuse' &&
      (!confirmReuse.value || previous?.personId !== result.personId)
    ) {
      confirmReuse.value = false
      return
    }
    const payload = {
      ...intentValues,
      reusePersonId: result.status === 'reuse' ? result.personId : undefined,
      confirmReuse: result.status === 'reuse',
      idempotencyKey: generateUUID()
    }
    command = { intent, payload }
    emit('saved', await Api.create(payload))
  } catch (e: any) {
    if (Number(e?.code) >= 1_000_000) command = undefined
    error.value = e?.msg || e?.message || '添加学员失败，请重试'
  } finally {
    busy.value = false
    emit('busy', false)
  }
}
onMounted(load)
</script>
