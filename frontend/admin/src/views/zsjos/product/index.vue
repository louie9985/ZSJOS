<template>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="mb-16px">
      <template #default
        ><el-button link type="primary" @click="load">重新加载</el-button></template
      >
    </el-alert>
    <el-row v-else :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="panel-heading"
          ><span>课程分类树</span
          ><span>
            <el-button
              v-if="selectedCategory"
              link
              type="primary"
              v-hasPermi="['zsjos:product-category:update']"
              @click="openCategory(selectedCategory)"
              >编辑</el-button
            >
            <ZsjosPopconfirm
              v-if="selectedCategory"
              :action="`删除产品分类「${selectedCategory.name}」`"
              danger
              @confirm="removeCategory(selectedCategory)"
            >
              <el-button
                link
                type="danger"
                :loading="isProcessing(`category-delete:${selectedCategory.id}`)"
                v-hasPermi="['zsjos:product-category:delete']"
                >删除</el-button
              >
            </ZsjosPopconfirm>
            <el-button
              link
              type="primary"
              v-hasPermi="['zsjos:product-category:create']"
              @click="openCategory()"
              ><Icon icon="ep:plus" />新增</el-button
            >
          </span></div
        >
        <el-empty v-if="!categories.length" description="暂无分类，可先创建根分类" />
        <el-tree
          v-else
          node-key="id"
          :data="categories"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="selectCategory"
        >
          <template #default="{ data }"
            ><span class="tree-node"
              ><span>{{ data.name }}</span
              ><span>
                <el-tag size="small" type="info">第 {{ data.level }} 层</el-tag>
                <el-tag v-if="data.hasProducts" size="small">有课程</el-tag>
                <el-tag v-if="data.status !== 0" size="small" type="info">停用</el-tag>
                <el-button
                  link
                  size="small"
                  :loading="isProcessing(`category-status:${data.id}`)"
                  v-hasPermi="['zsjos:product-category:status']"
                  @click.stop="toggleCategory(data)"
                  >{{ data.status === 0 ? '停用' : '启用' }}</el-button
                >
              </span></span
            ></template
          >
        </el-tree>
      </el-col>
      <el-col :xs="24" :md="16">
        <div class="panel-heading"
          ><span>课程 SPU</span
          ><el-button
            type="primary"
            size="small"
            :disabled="!selectedCategory || !!selectedCategory.children?.length"
            v-hasPermi="['zsjos:product:create']"
            @click="openProduct()"
            ><Icon icon="ep:plus" class="mr-5px" />新增</el-button
          ></div
        >
        <el-alert
          v-if="selectedCategory?.children?.length"
          type="info"
          :closable="false"
          title="当前分类还有子分类，SPU 只能挂在叶子分类。"
        />
        <el-empty v-if="!selectedCategory" description="请选择分类" />
        <el-empty v-else-if="!products.length" description="暂无产品" />
        <el-table v-else :data="products" size="small" :show-overflow-tooltip="true">
          <el-table-column label="课程名称" prop="name" min-width="150" />
          <el-table-column label="稳定编号" prop="productRef" min-width="210" />
          <el-table-column label="状态" width="70"
            ><template #default="scope"
              ><el-tag size="small" :type="scope.row.status === 0 ? 'success' : 'info'">{{
                scope.row.status === 0 ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="操作" width="280" fixed="right"
            ><template #default="scope"
              ><el-button
                link
                type="primary"
                v-hasPermi="['zsjos:product:update']"
                @click="openProduct(scope.row)"
                >编辑</el-button
              ><el-button
                link
                type="primary"
                v-hasPermi="['zsjos:product:sku-query']"
                @click="openSkuConfig(scope.row)"
                >属性/SKU</el-button
              ><el-button
                link
                v-hasPermi="['zsjos:product:status']"
                :loading="isProcessing(`product-status:${scope.row.id}`)"
                @click="toggleProduct(scope.row)"
                >{{ scope.row.status === 0 ? '停用' : '启用' }}</el-button
              ><ZsjosPopconfirm
                :action="`删除课程 SPU「${scope.row.name}」`"
                danger
                @confirm="removeProduct(scope.row)"
              >
                <el-button
                  link
                  type="danger"
                  :loading="isProcessing(`product-delete:${scope.row.id}`)"
                  v-hasPermi="['zsjos:product:delete']"
                  >删除</el-button
                >
              </ZsjosPopconfirm></template
            ></el-table-column
          >
        </el-table>
      </el-col>
    </el-row>
  </ContentWrap>

  <el-dialog
    v-model="categoryDialog"
    :title="categoryEditing ? '编辑分类' : '新增分类'"
    width="460px"
  >
    <el-form ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="90px">
      <el-form-item label="分类名称" prop="name"
        ><el-input v-model="categoryForm.name" maxlength="100"
      /></el-form-item>
      <el-form-item label="父分类">
        <el-cascader
          v-model="categoryForm.parentId"
          class="w-full"
          :options="parentCategoryOptions"
          :props="parentCascaderProps"
          clearable
          placeholder="清空表示根分类"
        />
      </el-form-item>
      <el-form-item label="排序" prop="sort"
        ><el-input-number v-model="categoryForm.sort" :min="0"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-switch
          v-model="categoryForm.status"
          :active-value="0"
          :inactive-value="1"
          active-text="启用"
          inactive-text="停用"
      /></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="categoryForm.remark" type="textarea" maxlength="1000"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="categoryDialog = false">取消</el-button
      ><el-button v-if="!categoryEditing" type="primary" :loading="saving" @click="saveCategory"
        >保存</el-button
      ><ZsjosPopconfirm
        v-else
        :action="`编辑产品分类「${categoryForm.name}」`"
        v-model:visible="categoryConfirmVisible"
        @confirm="saveCategory"
        ><el-button type="primary" :loading="saving" @click="prepareCategorySave"
          >保存</el-button
        ></ZsjosPopconfirm
      ></template
    >
  </el-dialog>

  <el-dialog
    v-model="productDialog"
    :title="productEditing ? '编辑课程 SPU' : '新增课程 SPU'"
    width="720px"
  >
    <el-form ref="productFormRef" :model="productForm" :rules="productRules" label-width="90px">
      <el-form-item v-if="productEditing" label="稳定编号"
        ><el-input :model-value="productForm.productRef" disabled
      /></el-form-item>
      <el-form-item label="所属分类" prop="categoryId"
        ><el-cascader
          v-model="productForm.categoryId"
          class="w-full"
          :options="productCategoryOptions"
          :props="productCascaderProps"
          clearable
      /></el-form-item>
      <el-form-item label="课程名称" prop="name"
        ><el-input v-model="productForm.name" maxlength="200"
      /></el-form-item>
      <el-form-item label="副标题"
        ><el-input v-model="productForm.subtitle" maxlength="200"
      /></el-form-item>
      <el-form-item label="适用人群"
        ><el-input v-model="productForm.targetAudience" maxlength="500"
      /></el-form-item>
      <el-form-item label="学习时长"
        ><el-input v-model="productForm.studyDuration" maxlength="100"
      /></el-form-item>
      <el-form-item label="学习方式"
        ><el-input v-model="productForm.studyMode" maxlength="100"
      /></el-form-item>
      <el-form-item label="封面地址"
        ><el-input v-model="productForm.coverImage" maxlength="1024"
      /></el-form-item>
      <el-form-item label="课程详情"
        ><el-input v-model="productForm.description" type="textarea" :rows="5"
      /></el-form-item>
      <el-form-item label="排序" prop="sort"
        ><el-input-number v-model="productForm.sort" :min="0"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-switch
          v-model="productForm.status"
          :active-value="0"
          :inactive-value="1"
          active-text="启用"
          inactive-text="停用"
      /></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="productForm.remark" type="textarea" maxlength="1000"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="productDialog = false">取消</el-button
      ><el-button v-if="!productEditing" type="primary" :loading="saving" @click="saveProduct"
        >保存</el-button
      ><ZsjosPopconfirm
        v-else
        :action="`编辑课程 SPU「${productForm.name}」`"
        v-model:visible="productConfirmVisible"
        @confirm="saveProduct"
        ><el-button type="primary" :loading="saving" @click="prepareProductSave"
          >保存</el-button
        ></ZsjosPopconfirm
      ></template
    >
  </el-dialog>

  <el-dialog
    v-model="skuDialog"
    :title="`${currentSpu?.name || ''} - 销售属性与 SKU`"
    width="920px"
  >
    <el-tabs v-model="skuTab">
      <el-tab-pane label="销售属性" name="attrs">
        <el-alert
          type="info"
          :closable="false"
          class="mb-16px"
          title="修改属性后请重新生成缺失的 SKU 组合；已存在 SKU 不会被自动删除。"
        />
        <div v-for="(attr, index) in attrForms" :key="attr.attrKey || index" class="attr-card">
          <el-row :gutter="12">
            <el-col :span="7"
              ><el-input v-model="attr.attrName" placeholder="属性名，如：级别"
            /></el-col>
            <el-col :span="12"
              ><el-input v-model="attr.valuesText" placeholder="属性值，以英文逗号分隔"
            /></el-col>
            <el-col :span="3"><el-checkbox v-model="attr.required">必填</el-checkbox></el-col>
            <el-col :span="2"
              ><el-button link type="danger" @click="attrForms.splice(index, 1)"
                >删除</el-button
              ></el-col
            >
          </el-row>
        </div>
        <el-button type="primary" plain @click="addAttr">添加属性</el-button>
        <ZsjosPopconfirm
          :action="`保存课程「${currentSpu?.name || ''}」的销售属性`"
          v-model:visible="attrsConfirmVisible"
          @confirm="saveAttrs"
        >
          <el-button
            type="primary"
            :loading="saving"
            v-hasPermi="['zsjos:product:attr-update']"
            @click="prepareAttrsSave"
            >保存属性</el-button
          >
        </ZsjosPopconfirm>
      </el-tab-pane>
      <el-tab-pane label="SKU与价格" name="skus">
        <div class="mb-16px"
          ><el-button
            type="primary"
            :loading="Boolean(currentSpu && isProcessing(`sku-generate:${currentSpu.id}`))"
            v-hasPermi="['zsjos:product:sku-create']"
            @click="generateSku"
            >生成缺失组合</el-button
          ></div
        >
        <el-table :data="skus" size="small">
          <el-table-column label="SKU名称" prop="skuName" min-width="180" />
          <el-table-column label="属性组合" min-width="180"
            ><template #default="scope">{{
              formatAttrs(scope.row.attrValues)
            }}</template></el-table-column
          >
          <el-table-column label="价格" width="100"
            ><template #default="scope"
              >¥{{ Number(scope.row.price).toFixed(2) }}</template
            ></el-table-column
          >
          <el-table-column label="状态" width="70"
            ><template #default="scope">{{
              scope.row.status === 0 ? '启用' : '停用'
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="180"
            ><template #default="scope"
              ><el-button
                link
                type="primary"
                v-hasPermi="['zsjos:product:sku-update']"
                @click="openSku(scope.row)"
                >编辑</el-button
              ><el-button
                link
                v-hasPermi="['zsjos:product:sku-status']"
                :loading="isProcessing(`sku-status:${scope.row.id}`)"
                @click="toggleSku(scope.row)"
                >{{ scope.row.status === 0 ? '停用' : '启用' }}</el-button
              ><el-button
                link
                type="danger"
                :loading="isProcessing(`sku-delete:${scope.row.id}`)"
                v-hasPermi="['zsjos:product:sku-delete']"
                @click="removeSku(scope.row)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>

  <el-dialog v-model="skuEditDialog" title="编辑 SKU" width="560px">
    <el-form :model="skuForm" label-width="90px">
      <el-form-item label="SKU名称"
        ><el-input v-model="skuForm.skuName" maxlength="200"
      /></el-form-item>
      <el-form-item label="属性组合"
        ><el-input :model-value="formatAttrs(skuForm.attrValues)" disabled
      /></el-form-item>
      <el-form-item label="价格"
        ><el-input-number v-model="skuForm.price" :min="0" :precision="2" class="w-full"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-switch v-model="skuForm.status" :active-value="0" :inactive-value="1"
      /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="skuForm.sort" :min="0" /></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="skuForm.remark" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="skuEditDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveSku">保存</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import * as ProductApi from '@/api/zsjos/product'
import ZsjosPopconfirm from '../components/ZsjosPopconfirm.vue'

defineOptions({ name: 'ZsjosProduct' })
const message = useMessage()
const loading = ref(true)
const saving = ref(false)
const categoryConfirmVisible = ref(false)
const productConfirmVisible = ref(false)
const attrsConfirmVisible = ref(false)
const processingKeys = ref(new Set<string>())
const isProcessing = (key: string) => processingKeys.value.has(key)
const withProcessing = async (key: string, task: () => Promise<void>) => {
  if (processingKeys.value.has(key)) return
  processingKeys.value = new Set(processingKeys.value).add(key)
  try {
    await task()
  } finally {
    const next = new Set(processingKeys.value)
    next.delete(key)
    processingKeys.value = next
  }
}
const error = ref('')
const categories = ref<ProductApi.ZsjosProductCategoryVO[]>([])
const selectedCategory = ref<ProductApi.ZsjosProductCategoryVO>()
const products = ref<ProductApi.ZsjosProductVO[]>([])
const categoryDialog = ref(false)
const categoryEditing = ref(false)
const categoryFormRef = ref<FormInstance>()
const productFormRef = ref<FormInstance>()
const productDialog = ref(false)
const productEditing = ref(false)
const skuDialog = ref(false)
const skuEditDialog = ref(false)
const skuTab = ref('attrs')
const currentSpu = ref<ProductApi.ZsjosProductVO>()
const skus = ref<ProductApi.ProductSkuVO[]>([])
const attrForms = ref<Array<ProductApi.ProductAttrVO & { valuesText: string }>>([])
const skuForm = reactive<ProductApi.ProductSkuSaveReqVO>({
  spuId: 0,
  skuName: '',
  attrValues: {},
  price: 0,
  status: 1,
  sort: 0,
  remark: ''
})
const categoryForm = reactive<
  Partial<ProductApi.ZsjosProductCategoryVO> & {
    name: string
    sort: number
    status: number
    remark: string
    parentId?: number
  }
>({ name: '', sort: 0, status: 0, remark: '' })
const productForm = reactive<ProductApi.ZsjosProductSaveReqVO & { productRef?: string }>({
  name: '',
  categoryId: 0,
  status: 0,
  sort: 0,
  remark: ''
})
const categoryRules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'change' }]
}
const productRules: FormRules = {
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择叶子分类', trigger: 'change' }]
}
const collectIds = (node?: ProductApi.ZsjosProductCategoryVO): Set<number> => {
  const ids = new Set<number>()
  const visit = (item?: ProductApi.ZsjosProductCategoryVO) => {
    if (!item) return
    ids.add(item.id)
    item.children?.forEach(visit)
  }
  visit(node)
  return ids
}
const toCascader = (
  nodes: ProductApi.ZsjosProductCategoryVO[],
  mode: 'parent' | 'product',
  disabledIds = new Set<number>()
): any[] =>
  nodes.map((node) => ({
    value: node.id,
    label: `${node.name}${node.status === 0 ? '' : '（停用）'}`,
    disabled:
      node.status !== 0 ||
      disabledIds.has(node.id) ||
      (mode === 'parent' && (node.hasProducts || node.level >= 10)) ||
      (mode === 'product' && !!node.children?.length),
    children: node.children?.length ? toCascader(node.children, mode, disabledIds) : undefined
  }))
const parentCategoryOptions = computed(() =>
  toCascader(
    categories.value,
    'parent',
    collectIds(categoryEditing.value ? selectedCategory.value : undefined)
  )
)
const productCategoryOptions = computed(() => toCascader(categories.value, 'product'))
const parentCascaderProps = {
  checkStrictly: true,
  emitPath: false,
  value: 'value',
  label: 'label',
  children: 'children'
}
const productCascaderProps = {
  checkStrictly: true,
  emitPath: false,
  value: 'value',
  label: 'label',
  children: 'children'
}

const findCategory = (
  id: number,
  nodes = categories.value
): ProductApi.ZsjosProductCategoryVO | undefined => {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findCategory(id, node.children || [])
    if (found) return found
  }
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    categories.value = await ProductApi.getCategoryTree()
    if (selectedCategory.value) selectedCategory.value = findCategory(selectedCategory.value.id)
    await loadProducts()
  } catch (e: any) {
    error.value = e?.msg || e?.message || '产品分类加载失败'
  } finally {
    loading.value = false
  }
}
const selectCategory = async (row: ProductApi.ZsjosProductCategoryVO) => {
  selectedCategory.value = row
  await loadProducts()
}
const loadProducts = async () => {
  if (!selectedCategory.value) {
    products.value = []
    return
  }
  const data = await ProductApi.getProductPage({
    pageNo: 1,
    pageSize: 100,
    categoryId: selectedCategory.value.id
  })
  products.value = data.list || []
}
const openCategory = (row?: ProductApi.ZsjosProductCategoryVO) => {
  categoryEditing.value = !!row
  const defaultParent =
    selectedCategory.value &&
    selectedCategory.value.status === 0 &&
    !selectedCategory.value.hasProducts &&
    selectedCategory.value.level < 10
      ? selectedCategory.value.id
      : 0
  Object.assign(
    categoryForm,
    row || {
      id: undefined,
      parentId: defaultParent,
      name: '',
      sort: 0,
      status: 0,
      remark: ''
    }
  )
  categoryDialog.value = true
}
const saveCategory = async () => {
  categoryConfirmVisible.value = false
  if (saving.value) return
  if (!(await categoryFormRef.value?.validate())) return
  saving.value = true
  try {
    if (categoryEditing.value) await ProductApi.updateCategory(categoryForm)
    else await ProductApi.createCategory(categoryForm)
    categoryDialog.value = false
    await load()
    message.success('分类已保存')
  } finally {
    saving.value = false
  }
}
const prepareCategorySave = async () => {
  if (!(await categoryFormRef.value?.validate())) return
  categoryConfirmVisible.value = true
}
const openProduct = (row?: ProductApi.ZsjosProductVO) => {
  productEditing.value = !!row
  Object.assign(
    productForm,
    row || {
      id: undefined,
      categoryId: selectedCategory.value?.id || 0,
      name: '',
      status: 0,
      sort: 0,
      remark: ''
    }
  )
  productDialog.value = true
}
const saveProduct = async () => {
  productConfirmVisible.value = false
  if (saving.value) return
  if (!(await productFormRef.value?.validate())) return
  saving.value = true
  try {
    if (productEditing.value) await ProductApi.updateProduct(productForm)
    else await ProductApi.createProduct(productForm)
    productDialog.value = false
    await loadProducts()
    message.success('产品已保存')
  } finally {
    saving.value = false
  }
}
const prepareProductSave = async () => {
  if (!(await productFormRef.value?.validate())) return
  productConfirmVisible.value = true
}
const toggleProduct = async (row: ProductApi.ZsjosProductVO) => {
  await withProcessing(`product-status:${row.id}`, async () => {
    await ProductApi.updateProductStatus({ id: row.id, status: row.status === 0 ? 1 : 0 })
    await loadProducts()
    message.success('状态已更新')
  })
}
const removeProduct = async (row: ProductApi.ZsjosProductVO) => {
  await withProcessing(`product-delete:${row.id}`, async () => {
    try {
      await ProductApi.deleteProduct(row.id)
      await loadProducts()
      message.success('产品已删除')
    } catch (e: any) {
      if (e?.msg) message.error(e.msg)
    }
  })
}
const toggleCategory = async (row: ProductApi.ZsjosProductCategoryVO) => {
  await withProcessing(`category-status:${row.id}`, async () => {
    await ProductApi.updateCategoryStatus(row.id, row.status === 0 ? 1 : 0)
    await load()
    message.success('分类状态已更新')
  })
}
const removeCategory = async (row?: ProductApi.ZsjosProductCategoryVO) => {
  if (!row) return
  await withProcessing(`category-delete:${row.id}`, async () => {
    try {
      await ProductApi.deleteCategory(row.id)
      if (selectedCategory.value?.id === row.id) selectedCategory.value = undefined
      await load()
      message.success('分类已删除')
    } catch (e: any) {
      if (e?.msg) message.error(e.msg)
    }
  })
}
const openSkuConfig = async (row: ProductApi.ZsjosProductVO) => {
  currentSpu.value = row
  skuDialog.value = true
  skuTab.value = 'attrs'
  const [attrs, skuItems] = await Promise.all([
    ProductApi.getProductAttrs(row.id),
    ProductApi.getSkuList(row.id)
  ])
  attrForms.value = (attrs || []).map((attr: ProductApi.ProductAttrVO) => ({
    ...attr,
    valuesText: attr.values.map((item) => item.label).join(',')
  }))
  skus.value = skuItems || []
}
const addAttr = () =>
  attrForms.value.push({
    attrName: '',
    required: true,
    sort: attrForms.value.length,
    values: [],
    valuesText: ''
  })
const saveAttrs = async () => {
  attrsConfirmVisible.value = false
  if (saving.value) return
  if (!currentSpu.value) return
  const attrs = attrForms.value.map((attr, index) => ({
    attrKey: attr.attrKey,
    attrName: attr.attrName.trim(),
    required: attr.required,
    sort: index,
    values: attr.valuesText
      .split(',')
      .map((value, valueIndex) => ({ value: value.trim(), label: value.trim(), sort: valueIndex }))
      .filter((item) => item.value)
  }))
  if (attrs.some((attr) => !attr.attrName || !attr.values.length))
    return message.warning('请完整填写属性名称和属性值')
  saving.value = true
  try {
    await ProductApi.saveProductAttrs(currentSpu.value.id, attrs)
    message.success('销售属性已保存')
    await openSkuConfig(currentSpu.value)
  } finally {
    saving.value = false
  }
}
const prepareAttrsSave = () => {
  if (!currentSpu.value) return
  const invalid = attrForms.value.some(
    (attr) => !attr.attrName.trim() || !attr.valuesText.split(',').some((value) => value.trim())
  )
  if (invalid) return message.warning('请完整填写属性名称和属性值')
  attrsConfirmVisible.value = true
}
const generateSku = async () => {
  if (!currentSpu.value) return
  const spuId = currentSpu.value.id
  await withProcessing(`sku-generate:${spuId}`, async () => {
    const count = await ProductApi.generateSkus(spuId)
    skus.value = await ProductApi.getSkuList(spuId)
    message.success(count ? `已生成 ${count} 个缺失组合，请设置价格后启用` : '没有需要生成的新组合')
  })
}
const formatAttrs = (attrs?: Record<string, string>) =>
  Object.values(attrs || {}).join(' / ') || '无销售属性'
const openSku = (row: ProductApi.ProductSkuVO) => {
  Object.assign(skuForm, row)
  skuEditDialog.value = true
}
const saveSku = async () => {
  if (saving.value) return
  saving.value = true
  try {
    await ProductApi.updateSku(skuForm)
    skuEditDialog.value = false
    if (currentSpu.value) skus.value = await ProductApi.getSkuList(currentSpu.value.id)
    message.success('SKU已保存')
  } finally {
    saving.value = false
  }
}
const toggleSku = async (row: ProductApi.ProductSkuVO) => {
  await withProcessing(`sku-status:${row.id}`, async () => {
    await ProductApi.updateSkuStatus(row.id, row.status === 0 ? 1 : 0)
    if (currentSpu.value) skus.value = await ProductApi.getSkuList(currentSpu.value.id)
  })
}
const removeSku = async (row: ProductApi.ProductSkuVO) => {
  await withProcessing(`sku-delete:${row.id}`, async () => {
    try {
      await message.delConfirm()
      await ProductApi.deleteSku(row.id)
      if (currentSpu.value) skus.value = await ProductApi.getSkuList(currentSpu.value.id)
    } catch (e: any) {
      if (e?.msg) message.error(e.msg)
    }
  })
}
onMounted(load)
</script>

<style scoped>
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.tree-node {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.menu-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attr-card {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
</style>
