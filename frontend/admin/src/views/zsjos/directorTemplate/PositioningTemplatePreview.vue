<template>
  <div class="positioning-preview" :class="{ mobile }">
    <el-empty v-if="!rows.length" description="暂无启用字段" />
    <template v-else>
      <div class="positioning-head">
        <span>定位卡项目</span><span>填写提示</span><span>计划交付内容确定</span
        ><span>参考账号与爆款</span>
      </div>
      <section v-for="row in rows" :key="row.field.key" class="positioning-row">
        <strong>{{ row.field.title }}{{ row.field.required ? ' *' : '' }}</strong>
        <div class="field-hint" data-label="填写提示">{{ row.field.description || '—' }}</div>
        <div
          v-for="(cell, index) in [[row.field], row.references]"
          :key="index"
          class="field-cell"
          :data-label="index ? '参考账号与爆款' : '计划交付内容确定'"
        >
          <span v-if="!cell.length">—</span>
          <div v-for="field in cell" :key="field.key" class="preview-control">
            <strong v-if="index">{{ field.title }}{{ field.required ? ' *' : '' }}</strong>
            <el-input
              v-if="['text', 'textarea'].includes(field.type)"
              disabled
              :type="field.type === 'textarea' ? 'textarea' : 'text'"
              :rows="3"
              :placeholder="field.title"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              disabled
              :model-value="undefined"
            />
            <el-select
              v-else-if="['select', 'multi_select', 'radio', 'checkbox_group'].includes(field.type)"
              disabled
              :placeholder="field.dictType ? '选项来自关联字典' : '请先关联系统字典'"
            />
            <el-switch v-else-if="field.type === 'checkbox'" disabled />
            <el-date-picker
              v-else-if="['date', 'datetime'].includes(field.type)"
              disabled
              :type="field.type === 'datetime' ? 'datetime' : 'date'"
              placeholder="选择日期"
            />
            <el-button v-else-if="field.type === 'material_picker'" disabled>选择素材</el-button>
            <el-button v-else-if="field.type === 'attachment'" disabled>上传附件</el-button>
            <el-input v-else-if="field.type === 'region'" disabled placeholder="选择地区" />
            <el-input
              v-else-if="field.type === 'system_history'"
              disabled
              placeholder="系统历史（只读）"
            />
            <el-input v-else disabled :placeholder="field.title" />
            <small v-if="field.recommendedCount">{{ field.recommendedCount }}</small>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DirectorField } from '@/api/zsjos/director'

const props = defineProps<{ fields: DirectorField[]; mobile: boolean }>()
const rows = computed(() => {
  const enabled = props.fields.filter((field) => field.enabled).sort((a, b) => a.sort - b.sort)
  // Keep orphaned references visible while their owner is disabled or being edited.
  const roots = enabled.filter(
    (field) =>
      !field.referenceFor ||
      !enabled.some((owner) => !owner.referenceFor && owner.key === field.referenceFor)
  )
  return roots.map((field) => ({
    field,
    references: enabled.filter((ref) => ref.referenceFor === field.key && ref.key !== field.key)
  }))
})
</script>

<style scoped>
.positioning-head,
.positioning-row {
  display: grid;
  grid-template-columns: minmax(100px, 0.8fr) minmax(120px, 1fr) minmax(180px, 1.5fr) minmax(
      160px,
      1.2fr
    );
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.positioning-head {
  font-weight: 600;
}

.positioning-row > *,
.preview-control {
  min-width: 0;
  overflow-wrap: anywhere;
}

.field-hint {
  color: var(--el-text-color-secondary);
  white-space: pre-wrap;
}

.preview-control + .preview-control {
  margin-top: 16px;
}

.preview-control > strong,
.preview-control > small {
  display: block;
  margin: 8px 0;
}

.preview-control :deep(.el-input),
.preview-control :deep(.el-select),
.preview-control :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.mobile .positioning-head {
  display: none;
}

.mobile .positioning-row {
  grid-template-columns: minmax(0, 1fr);
}

.mobile [data-label]::before {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  content: attr(data-label);
}

@media (width <= 760px) {
  .positioning-head {
    display: none;
  }

  .positioning-row {
    grid-template-columns: minmax(0, 1fr);
  }

  [data-label]::before {
    display: block;
    margin-bottom: 8px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    content: attr(data-label);
  }
}
</style>
