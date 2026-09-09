<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="状态"
        ><el-select v-model="query.status" class="!w-140px"
          ><el-option label="服务中" value="SERVING" /><el-option
            label="已结课"
            value="COMPLETED" /></el-select
      ></el-form-item>
      <el-form-item label="关键词"
        ><el-input
          v-model="query.keyword"
          clearable
          placeholder="班级名称 / 编号"
          @keyup.enter="load"
      /></el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="loading" @click="load"
          ><Icon icon="ep:search" class="mr-5px" />查询</el-button
        ><el-button
          v-if="!mine"
          v-hasPermi="['zsjos:delivery-class:create']"
          type="primary"
          @click="openEditor()"
          ><Icon icon="ep:plus" class="mr-5px" />创建班级</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"
      ><template #default
        ><el-button link type="primary" @click="load">重试</el-button></template
      ></el-alert
    >
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="id" stripe @row-click="openDetail">
      <el-table-column label="班级编号" prop="classNo" min-width="180" /><el-table-column
        label="班级"
        prop="className"
        min-width="180"
      />
      <el-table-column
        label="产品分类"
        prop="categoryNameSnapshot"
        min-width="150"
      /><el-table-column label="考期" prop="examScheduleSnapshot" min-width="160" />
      <el-table-column
        label="班主任"
        prop="homeroomUserNameSnapshot"
        min-width="120"
      /><el-table-column label="归属部门" prop="deptNameSnapshot" min-width="120" /><el-table-column
        label="人数"
        prop="studentCount"
        width="80"
      />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'SERVING' ? 'success' : 'info'">{{
            row.status === 'SERVING' ? '服务中' : '已结课'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column v-if="!mine" label="操作" width="150" fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="!row.systemClass && row.status === 'SERVING'"
            v-hasPermi="['zsjos:delivery-class:update']"
            link
            type="primary"
            @click.stop="openEditor(row)"
            >编辑</el-button
          ><el-button
            v-if="!row.systemClass && row.status === 'SERVING'"
            v-hasPermi="['zsjos:delivery-class:complete']"
            link
            type="danger"
            @click.stop="finish(row)"
            >结课</el-button
          ></template
        ></el-table-column
      >
      <template #empty><el-empty description="暂无班级" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <el-drawer
    v-model="detailOpen"
    :title="selected?.className || '班级详情'"
    size="760px"
    destroy-on-close
    @closed="resetDetail"
  >
    <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false"
      ><template #default
        ><el-button link @click="selected && loadStudents(selected, studentQuery.pageNo)"
          >重试</el-button
        ></template
      ></el-alert
    >
    <el-table v-else v-loading="detailLoading" :data="students" row-key="serviceRelationId">
      <el-table-column label="学员" prop="studentName" /><el-table-column
        label="学员编号"
        prop="personNo"
      /><el-table-column label="接收状态" prop="acceptanceStatus" /><el-table-column
        label="服务状态"
        prop="serviceStatus"
      /><el-table-column label="规划师" prop="ownerUserName" />
      <el-table-column label="操作" width="110"
        ><template #default="{ row }"
          ><el-button
            v-if="!mine"
            v-hasPermi="['zsjos:delivery-class:direct-transfer']"
            link
            type="primary"
            @click="openTransfer(row)"
            >直接调班</el-button
          ><el-button
            v-else
            v-hasPermi="['zsjos:class-transfer:create']"
            link
            type="primary"
            @click="openTransfer(row)"
            >申请调班</el-button
          ></template
        ></el-table-column
      >
      <template #empty><el-empty description="班内暂无学员" /></template>
    </el-table>
    <Pagination
      v-if="!detailError"
      :total="studentTotal"
      v-model:page="studentQuery.pageNo"
      v-model:limit="studentQuery.pageSize"
      @pagination="loadStudentPage"
    />
  </el-drawer>

  <ContentWrap v-if="mine && canQueryTransfers">
    <h3>我的调班申请</h3>
    <el-table :data="transferRecords" row-key="id" size="small">
      <el-table-column label="原班级" prop="fromClassName" /><el-table-column
        label="目标班级"
        prop="targetClassName"
      /><el-table-column label="原因" prop="reason" show-overflow-tooltip />
      <el-table-column label="状态"
        ><template #default="{ row }">{{
          transferStatusName(row.status)
        }}</template></el-table-column
      >
      <template #empty><el-empty description="暂无调班申请" /></template>
    </el-table>
  </ContentWrap>

  <Dialog v-model="editorOpen" :title="editing ? '编辑班级' : '创建班级'" width="560px">
    <el-form
      ref="editorRef"
      :model="editor"
      :rules="rules"
      label-width="90px"
      v-loading="referenceLoading"
    >
      <el-form-item label="班级名称" prop="className"
        ><el-input v-model="editor.className" maxlength="100" placeholder="留空时由系统生成"
      /></el-form-item>
      <el-form-item label="产品" prop="productId"><el-select v-model="editor.productId" filterable @change="productChanged"><el-option v-for="item in products" :key="item.productId" :label="item.productName" :value="item.productId" /></el-select></el-form-item>
      <el-form-item v-for="attr in selectedProduct?.attrs || []" :key="attr.attrKey" :label="attr.attrName"><el-select v-model="editor.selectedAttrs[attr.attrKey]" clearable @change="productScopeChanged"><el-option v-for="item in attr.values" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="SKU" prop="selectedSkuIds"><el-select v-model="editor.selectedSkuIds" multiple collapse-tags filterable :disabled="!selectedProduct"><el-option v-for="item in selectedProduct?.skus || []" :key="item.id" :label="item.skuName" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="考期" prop="examScheduleId"
        ><el-alert v-if="examError" :title="examError" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="reloadExams()">重试</el-button></template></el-alert><el-select v-model="editor.examScheduleId" :loading="examLoading" :disabled="examLoading || Boolean(examError)"
          ><el-option
            v-for="item in exams"
            :key="item.id"
            :label="item.displayName"
            :value="item.id" /></el-select
        ><el-empty v-if="!examLoading && !examError && !exams.length" description="暂无可用考期" :image-size="60" /></el-form-item>
      <el-form-item label="班主任" prop="homeroomUserId"
        ><el-select v-model="editor.homeroomUserId" filterable
          ><el-option
            v-for="item in candidates"
            :key="item.id"
            :label="`${item.name}${item.deptName ? ` · ${item.deptName}` : ''}`"
            :value="item.id" /></el-select
      ></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="editorOpen = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </Dialog>

  <Dialog
    v-model="transferOpen"
    :title="mine ? '申请调班' : '主管直接调班'"
    width="520px"
    @closed="resetTransfer"
  >
    <el-alert v-if="transferError" :title="transferError" type="error" show-icon :closable="false"
      ><template #default
        ><el-button
          link
          type="primary"
          @click="transferStudent && loadTransferOptions(transferStudent)"
          >重试</el-button
        ></template
      ></el-alert
    >
    <el-form ref="transferRef" :model="transferForm" :rules="transferRules" label-width="90px"
      ><el-form-item label="目标班级" prop="targetClassId"
        ><el-select
          v-model="transferForm.targetClassId"
          :loading="transferLoading"
          :disabled="transferLoading || Boolean(transferError)"
          ><el-option
            v-for="item in transferOptions.filter((item) => item.id !== selected?.id)"
            :key="item.id"
            :label="`${item.className} · ${item.homeroomUserName || '未配置班主任'}`"
            :value="item.id" /></el-select></el-form-item
      ><el-form-item label="调班原因" prop="reason"
        ><el-input
          v-model="transferForm.reason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="transferOpen = false">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="transferLoading || Boolean(transferError)"
        @click="submitTransfer"
        >确认调班</el-button
      ></template
    >
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as Api from '@/api/zsjos/deliveryClass'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ZsjosClassManagement' })
const props = withDefaults(defineProps<{ mine?: boolean }>(), { mine: false })
const mine = computed(() => props.mine)
const canQueryTransfers = computed(() => checkPermi(['zsjos:class-transfer:query']))
const query = reactive({ pageNo: 1, pageSize: 10, status: 'SERVING', keyword: '' })
const list = ref<Api.DeliveryClass[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const selected = ref<Api.DeliveryClass>()
const students = ref<Api.DeliveryClassStudent[]>([])
const studentQuery = reactive({ pageNo: 1, pageSize: 20 })
const studentTotal = ref(0)
const editorOpen = ref(false)
const editing = ref<Api.DeliveryClass>()
const editorRef = ref<FormInstance>()
const referenceLoading = ref(false)
const saving = ref(false)
const exams = ref<Api.ExamOption[]>([])
const examLoading = ref(false)
const examError = ref('')
const candidates = ref<Api.HomeroomCandidate[]>([])
const products = ref<Api.ProductOption[]>([])
const selectedProduct = computed(() => products.value.find(item => item.productId === editor.productId))
const normalizeAttrs = (attrs?: Record<string, string>) => Object.fromEntries(Object.entries(attrs || {}).filter(([, value]) => value != null && value !== ''))
const editor = reactive({
  className: '',
  categoryId: undefined as number | undefined,
  examScheduleId: undefined as number | undefined,
  homeroomUserId: undefined as number | undefined
  , productId: undefined as number | undefined, selectedSkuIds: [] as number[], selectedAttrs: {} as Record<string, string>
})
const rules: FormRules = {
  productId: [{ required: true, message: '请选择产品' }],
  examScheduleId: [{ required: true, message: '请选择考期' }],
  homeroomUserId: [{ required: true, message: '请选择班主任' }]
}
const transferOpen = ref(false)
const transferRef = ref<FormInstance>()
const transferStudent = ref<Api.DeliveryClassStudent>()
const transferOptions = ref<Api.DeliveryClassOption[]>([])
const transferLoading = ref(false)
const transferError = ref('')
const transferForm = reactive({ targetClassId: undefined as number | undefined, reason: '' })
const transferRules: FormRules = {
  targetClassId: [{ required: true, message: '请选择目标班级' }],
  reason: [{ required: true, message: '请填写调班原因' }]
}
const transferRecords = ref<Api.ClassTransfer[]>([])

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await Api.getDeliveryClassPage(query, mine.value)
    list.value = page.list
    total.value = page.total
    if (mine.value && canQueryTransfers.value)
      transferRecords.value = (await Api.getMyTransferPage()).list
  } catch (cause: any) {
    list.value = []
    total.value = 0
    error.value = cause?.msg || cause?.message || '班级加载失败'
  } finally {
    loading.value = false
  }
}
const loadStudents = async (row: Api.DeliveryClass, pageNo = studentQuery.pageNo) => {
  detailLoading.value = true
  detailError.value = ''
  try {
    const page = await Api.getDeliveryClassStudents(row.id, {
      pageNo,
      pageSize: studentQuery.pageSize
    })
    students.value = page.list
    studentTotal.value = page.total
    studentQuery.pageNo = pageNo
  } catch (cause: any) {
    students.value = []
    studentTotal.value = 0
    detailError.value = cause?.msg || cause?.message || '班级学员加载失败'
  } finally {
    detailLoading.value = false
  }
}
const openDetail = async (row: Api.DeliveryClass) => {
  selected.value = row
  detailOpen.value = true
  students.value = []
  studentTotal.value = 0
  studentQuery.pageNo = 1
  await loadStudents(row, 1)
}
const loadStudentPage = () => {
  if (selected.value) void loadStudents(selected.value, studentQuery.pageNo)
}
const resetDetail = () => {
  selected.value = undefined
  students.value = []
  studentTotal.value = 0
  studentQuery.pageNo = 1
  detailError.value = ''
}
const openEditor = async (row?: Api.DeliveryClass) => {
  editing.value = row
  editorOpen.value = true
  referenceLoading.value = true
  exams.value = []
  examError.value = ''
  Object.assign(editor, {
    className: row?.className || '',
    categoryId: row?.categoryId,
    examScheduleId: row?.examScheduleId,
    homeroomUserId: row?.homeroomUserId
    , productId: row?.productId, selectedSkuIds: row?.selectedSkus?.map(sku => sku.id) || [], selectedAttrs: { ...(row?.selectedAttrs || {}) }
  })
  try {
    const [productRows, candidateRows] = await Promise.all([
      Api.getProductOptions(),
      Api.getHomeroomCandidates()
    ])
    products.value = productRows; candidates.value = candidateRows
    editor.selectedAttrs = normalizeAttrs(row?.selectedAttrs)
    if (row?.categoryId) await reloadExams(row.categoryId, row.productId, editor.selectedAttrs)
  } catch (cause: any) {
    ElMessage.error(cause?.msg || cause?.message || '基础选项加载失败')
    editorOpen.value = false
  } finally {
    referenceLoading.value = false
  }
}
const reloadExams = async (categoryId = selectedProduct.value?.categoryId, productId = selectedProduct.value?.productId, attrs = editor.selectedAttrs) => {
  if (!categoryId) { exams.value = []; return }
  examLoading.value = true; examError.value = ''
  try { exams.value = await Api.getExamOptions(categoryId, productId, JSON.stringify(normalizeAttrs(attrs))) }
  catch (cause: any) { exams.value = []; examError.value = cause?.msg || cause?.message || '考期加载失败' }
  finally { examLoading.value = false }
}
const productChanged = (productId: number) => {
  const product = products.value.find(item => item.productId === productId)
  editor.categoryId = product?.categoryId
  editor.selectedSkuIds = product?.skus.map(sku => sku.id) || []
  editor.selectedAttrs = {}
  editor.examScheduleId = undefined
  void reloadExams(product?.categoryId, product?.productId, {})
}
const productScopeChanged = () => {
  const product = selectedProduct.value
  if (!product) return
  const attrs = normalizeAttrs(editor.selectedAttrs)
  editor.selectedAttrs = attrs
  const validSkuIds = new Set(product.skus.filter(sku => Object.entries(attrs).every(([key, value]) => sku.attrValues[key] === value)).map(sku => sku.id))
  editor.selectedSkuIds = editor.selectedSkuIds.filter(id => validSkuIds.has(id))
  editor.examScheduleId = undefined
  void reloadExams(product.categoryId, product.productId, editor.selectedAttrs)
}
const save = async () => {
  if (!(await editorRef.value?.validate())) return
  saving.value = true
  try {
    const value = {
      className: editor.className || undefined,
      productId: editor.productId!, selectedSkuIds: editor.selectedSkuIds,
      selectedAttrs: editor.selectedAttrs,
      categoryId: editor.categoryId!,
      examScheduleId: editor.examScheduleId!,
      homeroomUserId: editor.homeroomUserId!
    }
    if (editing.value)
      await Api.updateDeliveryClass(editing.value.id, { ...value, version: editing.value.version })
    else await Api.createDeliveryClass(value)
    ElMessage.success('班级已保存')
    editorOpen.value = false
    await load()
  } catch (cause: any) {
    ElMessage.error(cause?.msg || cause?.message || '班级保存失败')
  } finally {
    saving.value = false
  }
}
const finish = async (row: Api.DeliveryClass) => {
  await ElMessageBox.confirm(`确认结课“${row.className}”吗？`)
  try {
    await Api.completeDeliveryClass(row.id)
    ElMessage.success('班级已结课')
    await load()
  } catch (cause: any) {
    ElMessage.error(cause?.msg || cause?.message || '结课失败')
  }
}
const loadTransferOptions = async (row: Api.DeliveryClassStudent) => {
  transferLoading.value = true
  transferError.value = ''
  transferOptions.value = []
  try {
    transferOptions.value = await Api.getDeliveryClassOptions(row.categoryId!, false)
  } catch (cause: any) {
    transferError.value = cause?.msg || cause?.message || '目标班级加载失败'
  } finally {
    transferLoading.value = false
  }
}
const openTransfer = async (row: Api.DeliveryClassStudent) => {
  if (!row.categoryId) {
    ElMessage.error('该课程服务缺少产品分类，无法调班')
    return
  }
  transferStudent.value = row
  transferForm.targetClassId = undefined
  transferForm.reason = ''
  transferOptions.value = []
  transferError.value = ''
  transferOpen.value = true
  await loadTransferOptions(row)
}
const resetTransfer = () => {
  transferStudent.value = undefined
  transferOptions.value = []
  transferError.value = ''
  transferLoading.value = false
  transferForm.targetClassId = undefined
  transferForm.reason = ''
}
const submitTransfer = async () => {
  if (
    !transferStudent.value ||
    transferLoading.value ||
    transferError.value ||
    !(await transferRef.value?.validate())
  )
    return
  saving.value = true
  try {
    const data = {
      targetClassId: transferForm.targetClassId!,
      version: transferStudent.value.version,
      reason: transferForm.reason.trim()
    }
    if (mine.value) await Api.requestTransfer(transferStudent.value.serviceRelationId, data)
    else await Api.directTransfer(transferStudent.value.serviceRelationId, data)
    ElMessage.success(mine.value ? '调班申请已提交审批' : '调班已完成')
    transferOpen.value = false
    await load()
    if (selected.value) await loadStudents(selected.value, studentQuery.pageNo)
  } catch (cause: any) {
    ElMessage.error(cause?.msg || cause?.message || '调班失败')
  } finally {
    saving.value = false
  }
}
const transferStatusName = (status: string) =>
  ({
    pending: '审批中',
    approved: '已通过',
    rejected: '已拒绝',
    cancelled: '已取消',
    invalidated: '已失效'
  })[status] || status
onMounted(load)
</script>
