<template>
  <div v-loading="loading" class="mb-4">
    <el-alert v-if="error" type="error" :title="error" :closable="false"><template #default><el-button @click="load">重试</el-button></template></el-alert>
    <template v-else-if="detail">
      <el-alert v-if="detail.closed" type="warning" title="已关闭 · 异常结业：教学信息仅可查看，恢复不重新启用原任务及审批。" :closable="false" />
      <div class="my-3">
        <el-button v-if="detail.availableActions.includes('CLOSE_SERVICE') && checkPermi(['zsjos:student-service:close'])" type="danger" @click="open('close')">关闭服务</el-button>
        <el-button v-if="detail.availableActions.includes('RESTORE_SERVICE') && checkPermi(['zsjos:student-service:restore'])" type="primary" @click="open('restore')">恢复服务</el-button>
      </div>
      <el-collapse v-if="detail.history.length"><el-collapse-item title="关闭与恢复记录" name="history">
        <el-timeline><el-timeline-item v-for="(event, index) in detail.history" :key="index" :timestamp="event.occurredAt">
          {{ event.action === 'close' ? '关闭服务' : '恢复服务' }} · {{ event.operatorName || '人员姓名未记录' }}
          <div>{{ event.reason }}</div><div>{{ event.targetClassName }} · {{ event.targetOwnerName }}</div>
        </el-timeline-item></el-timeline>
      </el-collapse-item></el-collapse>
    </template>
    <el-dialog v-model="dialog" :title="mode === 'close' ? '关闭服务' : '恢复服务'" width="min(520px, 94vw)" append-to-body :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy">
      <el-alert v-if="submitError" type="error" :title="submitError" :closable="false" />
      <el-alert type="warning" :closable="false" :title="mode === 'close' ? '转入本人异常结业班，并取消当前教学待办、联系延期及教学调班审批。' : '恢复到所选班级，负责人随班主任确定；旧任务及审批不会重启。'" />
      <el-form label-position="top" class="mt-4" :disabled="busy">
        <template v-if="mode === 'restore'">
          <el-alert v-if="targetError" :title="targetError" type="error" :closable="false"><template #default><el-button @click="loadTargets">重试</el-button></template></el-alert>
          <el-empty v-else-if="!targetLoading && !targets.length" description="暂无可恢复的服务中班级" />
          <el-form-item label="恢复班级" required><el-select v-model="targetClassId" :loading="targetLoading" filterable class="w-full" placeholder="原班不可用时，请另选班级">
            <el-option v-for="row in targets" :key="row.id" :value="row.id" :label="`${row.className} · ${row.classNo} · ${row.homeroomUserName || '班主任姓名未记录'}`" />
          </el-select></el-form-item>
        </template>
        <el-form-item :label="mode === 'close' ? '关闭原因' : '恢复原因'" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="dialog = false">取消</el-button><el-button :type="mode === 'close' ? 'danger' : 'primary'" :loading="busy" :disabled="mode === 'restore' && (targetLoading || !!targetError || !targetClassId)" @click="submit">确认</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { checkPermi } from '@/utils/permission'
import { teachingLifecycleApi as api, type TeachingLifecycle } from '@/api/zsjos/registration/teachingLifecycle'
import type { DeliveryClassOption } from '@/api/zsjos/deliveryClass'
const props = defineProps<{ relationId: number; version: number }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const detail = ref<TeachingLifecycle>(), loading = ref(false), error = ref('')
const dialog = ref(false), busy = ref(false), mode = ref<'close' | 'restore'>('close')
const reason = ref(''), targetClassId = ref<number>(), targets = ref<DeliveryClassOption[]>([])
const targetLoading = ref(false), targetError = ref(''), requestKey = ref('')
const submitError = ref('')
const errorText = (cause: unknown) => cause instanceof Error ? cause.message : typeof cause === 'object' && cause !== null && 'msg' in cause && typeof cause.msg === 'string' ? cause.msg : '请求失败，请重试'
let generation = 0, targetGeneration = 0
onBeforeUnmount(() => { generation++; targetGeneration++ })
watch(dialog, value => { if (!value) targetGeneration++ })
async function load() {
  const token = ++generation; loading.value = true; error.value = ''; detail.value = undefined
  try { const value = await api.detail(props.relationId); if (token === generation) detail.value = value }
  catch (cause) { if (token === generation) error.value = errorText(cause) }
  finally { if (token === generation) loading.value = false }
}
async function loadTargets() {
  const token = ++targetGeneration
  targetLoading.value = true; targetError.value = ''; targets.value = []; targetClassId.value = undefined
  try {
    const rows = await api.targets(props.relationId)
    if (token !== targetGeneration) return
    targets.value = rows
    if (targetClassId.value == null && rows.some(row => row.id === detail.value?.previousClassId)) targetClassId.value = detail.value?.previousClassId
  }
  catch (cause) { if (token === targetGeneration) targetError.value = errorText(cause) }
  finally { if (token === targetGeneration) targetLoading.value = false }
}
function open(action: 'close' | 'restore') {
  submitError.value = ''
  mode.value = action; reason.value = ''; requestKey.value = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
  dialog.value = true; if (action === 'restore') void loadTargets()
}
async function submit() {
  if (busy.value) return
  if (!reason.value.trim()) { message.warning('请填写操作原因'); return }
  busy.value = true; submitError.value = ''
  try {
    await api[mode.value](props.relationId, { reason: reason.value.trim(), version: props.version, idempotencyKey: requestKey.value, ...(mode.value === 'restore' ? { targetClassId: targetClassId.value } : {}) })
    dialog.value = false; message.success(mode.value === 'close' ? '服务已关闭' : '服务已恢复'); emit('changed'); await load()
  } catch (cause) { submitError.value = errorText(cause) }
  finally { busy.value = false }
}
watch(() => [props.relationId, props.version], () => { targetGeneration++; dialog.value = false; void load() }, { immediate: true })
</script>
