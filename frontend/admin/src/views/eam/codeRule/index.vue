<template>
  <ContentWrap>
    <el-alert
      title="资产创建时按分类匹配编号规则；找不到分类规则时使用全局规则（适用分类为空的那条）"
      type="info"
      :closable="false"
      show-icon
      class="mb-3"
    />
    <el-button v-hasPermi="['eam:code-rule:create']" type="primary" @click="openForm('create')">
      <Icon icon="ep:plus" class="mr-5px" /> 新增规则
    </el-button>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="适用分类" min-width="140">
        <template #default="{ row }">
          <el-tag v-if="!row.categoryId" type="warning" size="small">全局默认</el-tag>
          <span v-else>{{ categoryName(row.categoryId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="前缀" prop="prefix" min-width="100" />
      <el-table-column label="拼接分类编码" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.useCategoryCode ? 'success' : 'info'" size="small">
            {{ row.useCategoryCode ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="日期格式" prop="dateFormat" min-width="110">
        <template #default="{ row }">{{ row.dateFormat || '不含日期' }}</template>
      </el-table-column>
      <el-table-column label="流水位数" prop="serialLength" width="90" align="center" />
      <el-table-column label="分隔符" prop="separator" width="80" align="center" />
      <el-table-column label="当前流水号" prop="currentSerial" width="110" align="center" />
      <el-table-column label="编号示例" min-width="160">
        <template #default="{ row }">
          <span class="font-mono">{{ previewOf(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['eam:code-rule:update']"
            link
            type="primary"
            @click="openForm('update', row.id)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['eam:code-rule:delete']"
            link
            type="danger"
            @click="handleDelete(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <CodeRuleForm ref="formRef" :category-tree="categoryTree" @success="getList" />
</template>

<script setup lang="ts">
import { handleTree } from '@/utils/tree'
import * as CodeRuleApi from '@/api/eam/codeRule'
import * as CategoryApi from '@/api/eam/category'
import CodeRuleForm from './CodeRuleForm.vue'

defineOptions({ name: 'EamCodeRule' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const list = ref<CodeRuleApi.CodeRuleVO[]>([])
const categories = ref<CategoryApi.CategoryVO[]>([])
const categoryTree = ref<any[]>([])

const categoryName = (id: number) =>
  categories.value.find((c) => c.id === id)?.name ?? `分类#${id}`

/** 按规则拼一个示例编号，让管理员保存前就能看清格式 */
const previewOf = (row: CodeRuleApi.CodeRuleVO) => {
  const sep = row.separator || '-'
  const segments: string[] = []
  if (row.prefix) {
    segments.push(row.prefix)
  }
  if (row.useCategoryCode) {
    const code = row.categoryId
      ? categories.value.find((c) => c.id === row.categoryId)?.code
      : undefined
    segments.push(code || 'XX')
  }
  if (row.dateFormat) {
    const now = new Date()
    const year = String(now.getFullYear())
    const month = String(now.getMonth() + 1).padStart(2, '0')
    segments.push(row.dateFormat === 'yyyyMM' ? `${year}${month}` : year)
  }
  const next = (row.currentSerial ?? 0) + 1
  segments.push(String(next).padStart(row.serialLength ?? 4, '0'))
  return segments.join(sep)
}

const getList = async () => {
  loading.value = true
  try {
    list.value = await CodeRuleApi.getCodeRuleList()
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
    await CodeRuleApi.deleteCodeRule(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  const [categoryRows] = await Promise.all([
    CategoryApi.getCategoryList(),
    getList()
  ])
  categories.value = categoryRows
  categoryTree.value = handleTree(categories.value as any, 'id', 'parentId')
})
</script>
