<template>
  <section class="feedback-approval">
    <div class="feedback-approval-heading">
      <h4>审批流程</h4>
      <el-select v-if="data" :model-value="data.roundNo" aria-label="审批轮次" class="!w-180px" @change="changeRound">
        <el-option v-for="round in data.rounds" :key="round.roundNo" :value="round.roundNo"
          :label="`第 ${round.roundNo} 轮${round.roundNo === data.latestRoundNo ? '（最新）' : ''}`" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新流程</el-button>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon><el-button link @click="load">重试</el-button></el-alert>
    <el-skeleton v-if="loading" animated :rows="4" />
    <template v-else-if="data">
      <el-alert v-if="data.availability !== 'AVAILABLE'" :type="data.availability === 'NOT_REQUIRED' ? 'info' : 'warning'"
        :title="data.unavailableReason" :closable="false" show-icon>
        <el-button v-if="data.availability === 'UNAVAILABLE'" link @click="load">重试</el-button>
      </el-alert>
      <template v-if="data.progress">
        <p><strong>当前审批人：</strong>{{ data.progress.status === 1 ? currentNames : '本轮审批已结束' }}</p>
        <el-timeline v-if="data.progress.nodes.length">
          <el-timeline-item v-for="(node, index) in data.progress.nodes" :key="`${node.id}:${index}`"
            :type="node.status === 2 ? 'success' : node.status === 3 ? 'danger' : 'primary'">
            <strong>{{ node.name }}</strong> <el-tag size="small">{{ statusLabel(node.status) }}</el-tag>
            <div v-for="task in node.tasks" :key="task.id" class="feedback-approval-task">
              {{ task.assigneeName || task.ownerName || '待分配' }}
              <el-tag v-if="task.parentTaskId" size="small">加签</el-tag><el-tag size="small">{{ statusLabel(task.status) }}</el-tag>
              <div>到达：{{ task.createTime ? formatDate(task.createTime) : '-' }}<span v-if="task.endTime"> · 处理：{{ formatDate(task.endTime) }}</span></div>
              <div v-if="task.reason" class="feedback-approval-reason">审批意见：{{ task.reason }}</div>
            </div>
            <div v-if="!node.tasks.length && node.candidates.length">预计节点，候选审批人：{{ node.candidates.map(user => user.name || '未知用户').join('、') }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" :image-size="64" />
      </template>
      <p v-if="data.lastUrgedAt">上次催办：{{ formatDate(data.lastUrgedAt) }}</p>
      <h4>第 {{ data.roundNo }} 轮提交内容</h4>
      <slot :fields="data.fields" :values="data.values" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getApproval, type FeedbackApproval } from '@/api/zsjos/feedback'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ id: number }>()
const roundNo = ref<number>()
const data = ref<FeedbackApproval>()
const loading = ref(false)
const error = ref('')
let sequence = 0
const labels: Record<number, string> = { '-2': '已跳过', '-1': '未开始', 0: '等待前序审批', 1: '审批中', 2: '已通过', 3: '已拒绝', 4: '已取消', 5: '已退回', 7: '通过中' }
const statusLabel = (status?: number) => status == null ? '未知状态' : labels[status] || '未知状态'
const currentNames = computed(() => data.value?.progress?.currentTasks.map(task => `${task.name} · ${task.assigneeName || '待分配'}`).join('；') || '暂无可处理任务')
async function load() {
  const seq = ++sequence
  loading.value = true; error.value = ''; data.value = undefined
  try { const result = await getApproval(props.id, roundNo.value); if (seq === sequence) data.value = result }
  catch (cause) { if (seq === sequence) error.value = cause instanceof Error ? cause.message : '审批流程加载失败' }
  finally { if (seq === sequence) loading.value = false }
}
function changeRound(value: number) { roundNo.value = value; void load() }
watch(() => props.id, () => { roundNo.value = undefined; void load() }, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<style scoped>
.feedback-approval { min-width: 0; overflow-wrap: anywhere; margin-top: 16px; }
.feedback-approval-heading { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.feedback-approval-heading h4 { margin: 0; }
.feedback-approval-task { margin: 8px 0; color: var(--el-text-color-regular); }
.feedback-approval-reason { white-space: pre-wrap; }
</style>
