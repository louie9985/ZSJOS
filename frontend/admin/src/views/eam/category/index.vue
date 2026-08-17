<template>
  <ContentWrap>
    <el-row :gutter="16">
      <!-- 左侧分类树 -->
      <el-col :xs="24" :sm="10" :md="8">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <span class="font-medium">资产分类</span>
          <div class="flex gap-2">
            <el-button v-hasPermi="['eam:category:import']" plain size="small" @click="openImport">
              <Icon icon="ep:upload" class="mr-5px" /> 导入配置
            </el-button>
            <el-button
              v-hasPermi="['eam:category:create']"
              type="primary"
              plain
              size="small"
              @click="openForm('create')"
            >
              <Icon icon="ep:plus" class="mr-5px" /> 新增
            </el-button>
          </div>
        </div>
        <div class="mb-3 grid gap-2 sm:grid-cols-2">
          <el-input v-model="keyword" clearable placeholder="搜索分类名称或编码">
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
          <el-select v-model="rootFilter" clearable placeholder="全部顶级分类">
            <el-option
              v-for="item in rootOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
        <el-tree
          v-loading="loading"
          :data="filteredTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="flex w-full items-center justify-between pr-2">
              <span>
                {{ data.name }}
                <el-tag size="small" class="ml-1">{{ data.code }}</el-tag>
                <el-tag v-if="data.status === 1" size="small" type="info" class="ml-1">
                  已关闭
                </el-tag>
                <el-tag size="small" type="info" class="ml-1">
                  {{ data.managementMode === 2 ? '批量' : '单件' }} / {{ data.unit || '个' }}
                </el-tag>
              </span>
              <span class="ml-2 shrink-0">
                <el-button
                  v-hasPermi="['eam:category:create']"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openForm('create', undefined, data.id)"
                >
                  子类
                </el-button>
                <el-button
                  v-hasPermi="['eam:category:update']"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openForm('update', data.id)"
                >
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['eam:category:delete']"
                  link
                  type="danger"
                  size="small"
                  @click.stop="handleDelete(data.id)"
                >
                  删除
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!loading && filteredTree.length === 0" description="没有匹配的资产分类" />
      </el-col>

      <!-- 右侧：所选分类的自定义字段 -->
      <el-col :xs="24" :sm="14" :md="16">
        <FieldConfig :category-id="currentCategoryId" :category-name="currentCategoryName" />
      </el-col>
    </el-row>
  </ContentWrap>

  <CategoryForm ref="formRef" :category-list="categoryList" @success="getList" />
  <CategoryImportForm ref="importRef" @success="handleImportSuccess" />
</template>

<script setup lang="ts">
import { handleTree } from '@/utils/tree'
import * as CategoryApi from '@/api/eam/category'
import CategoryForm from './CategoryForm.vue'
import CategoryImportForm from './CategoryImportForm.vue'
import FieldConfig from './FieldConfig.vue'

defineOptions({ name: 'EamCategory' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const categoryList = ref<CategoryApi.CategoryVO[]>([])
const categoryTree = ref<any[]>([])
const keyword = ref('')
const rootFilter = ref<number>()
const currentCategoryId = ref<number | undefined>()
const currentCategoryName = ref<string>('')

const rootOptions = computed(() =>
  categoryList.value.filter(
    (item): item is CategoryApi.CategoryVO & { id: number } =>
      item.parentId === 0 && item.id != null
  )
)
const filteredTree = computed(() => {
  const source = rootFilter.value
    ? categoryTree.value.filter((item) => item.id === rootFilter.value)
    : categoryTree.value
  const search = keyword.value.trim().toLowerCase()
  if (!search) return source
  const filterNodes = (nodes: any[]): any[] =>
    nodes.flatMap((node) => {
      const children = filterNodes(node.children || [])
      const matched = `${node.name} ${node.code}`.toLowerCase().includes(search)
      return matched || children.length ? [{ ...node, children }] : []
    })
  return filterNodes(source)
})

const getList = async () => {
  loading.value = true
  try {
    categoryList.value = await CategoryApi.getCategoryList()
    categoryTree.value = handleTree(categoryList.value as any, 'id', 'parentId')
  } finally {
    loading.value = false
  }
}

const handleNodeClick = (data: CategoryApi.CategoryVO) => {
  currentCategoryId.value = data.id
  currentCategoryName.value = data.name
}

const formRef = ref()
const openForm = (type: string, id?: number, parentId?: number) => {
  formRef.value.open(type, id, parentId)
}

const importRef = ref()
const openImport = () => importRef.value.open()
const handleImportSuccess = async () => {
  await getList()
  currentCategoryId.value = undefined
  currentCategoryName.value = ''
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await CategoryApi.deleteCategory(id)
    message.success(t('common.delSuccess'))
    if (currentCategoryId.value === id) {
      currentCategoryId.value = undefined
      currentCategoryName.value = ''
    }
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
