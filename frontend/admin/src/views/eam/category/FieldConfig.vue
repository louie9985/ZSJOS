<template>
  <div class="mb-3 flex items-center justify-between">
    <span class="font-medium">
      自定义字段
      <span v-if="categoryName" class="text-gray-500">（{{ categoryName }}）</span>
    </span>
    <el-button
      v-hasPermi="['eam:category-field:create']"
      type="primary"
      plain
      size="small"
      :disabled="!categoryId"
      @click="openForm('create')"
    >
      <Icon icon="ep:plus" class="mr-5px" /> 新增字段
    </el-button>
  </div>

  <el-alert
    v-if="!categoryId"
    title="请先在左侧选择一个分类"
    type="info"
    :closable="false"
    show-icon
  />

  <template v-else>
    <el-table v-loading="loading" :data="list" row-key="id">
      <el-table-column label="字段名" prop="fieldName" min-width="120" />
      <el-table-column label="标识" prop="fieldKey" min-width="120" />
      <el-table-column label="类型" min-width="100">
        <template #default="{ row }">
          {{ fieldTypeName(row.fieldType) }}
        </template>
      </el-table-column>
      <el-table-column label="管理端" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.adminVisible" type="success" size="small">显示</el-tag>
          <span v-else class="text-gray-400">隐藏</span>
        </template>
      </el-table-column>
      <el-table-column label="员工收集表" min-width="120" align="center">
        <template #default="{ row }">
          <span v-if="!row.collectionVisible" class="text-gray-400">隐藏</span>
          <el-tag v-else-if="row.collectionRequired" type="danger" size="small">必填</el-tag>
          <el-tag v-else type="info" size="small">选填</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.inherited" type="info" size="small">继承</el-tag>
          <el-tag v-else type="success" size="small">本级</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" width="70" align="center" />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 继承字段属于父分类，只能到父分类上修改，此处禁用避免误改影响其他子类 -->
          <el-tooltip v-if="row.inherited" content="继承字段请到父分类修改" placement="top">
            <span>
              <el-button link type="primary" size="small" disabled>编辑</el-button>
            </span>
          </el-tooltip>
          <template v-else>
            <el-button
              v-hasPermi="['eam:category-field:update']"
              link
              type="primary"
              size="small"
              @click="openForm('update', row.id)"
            >
              编辑
            </el-button>
            <el-button
              v-hasPermi="['eam:category-field:delete']"
              link
              type="danger"
              size="small"
              @click="handleDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && list.length === 0" description="该分类暂无自定义字段" />
  </template>

  <FieldForm ref="formRef" :category-id="categoryId" @success="getList" />
</template>

<script setup lang="ts">
import * as CategoryFieldApi from '@/api/eam/categoryField'
import { FieldType } from '@/api/eam/categoryField'
import FieldForm from './FieldForm.vue'

defineOptions({ name: 'EamCategoryFieldConfig' })

const props = defineProps<{ categoryId?: number; categoryName?: string }>()

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const list = ref<CategoryFieldApi.CategoryFieldVO[]>([])

const FIELD_TYPE_NAMES: Record<number, string> = {
  [FieldType.TEXT]: '单行文本',
  [FieldType.TEXTAREA]: '多行文本',
  [FieldType.NUMBER]: '数字',
  [FieldType.DATE]: '日期',
  [FieldType.SELECT]: '下拉选择'
}
const fieldTypeName = (type: number) => FIELD_TYPE_NAMES[type] ?? '未知'

const getList = async () => {
  if (!props.categoryId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    // 用生效列表，让管理员直观看到本级字段与父级继承字段的合并结果
    list.value = await CategoryFieldApi.getEffectiveFieldList(props.categoryId)
  } finally {
    loading.value = false
  }
}

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await CategoryFieldApi.deleteField(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

watch(() => props.categoryId, getList, { immediate: true })
</script>
