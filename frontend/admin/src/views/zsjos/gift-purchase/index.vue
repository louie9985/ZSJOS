<template>
  <ContentWrap>
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="学员姓名"
        ><el-input v-model="query.studentName" clearable
      /></el-form-item>
      <el-form-item label="订单号"><el-input v-model="query.orderNo" clearable /></el-form-item>
      <el-form-item label="礼品"><el-input v-model="query.giftKeyword" clearable /></el-form-item>
      <el-form-item label="采购状态">
        <el-select v-model="query.purchased" style="width: 130px" aria-label="采购状态">
          <el-option label="全部" :value="'all'" />
          <el-option label="未采购" :value="false" />
          <el-option label="已采购" :value="true" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button v-hasPermi="['zsjos:gift-purchase:create']" type="success" @click="open()"
          >新增采购</el-button
        >
      </el-form-item>
    </el-form>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="mb-16px"
    >
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <el-table v-loading="loading" :data="rows" border empty-text="暂无采购记录">
      <el-table-column label="采购状态" width="100">
        <template #default="{ row }"
          ><el-tag :type="row.purchased ? 'success' : 'warning'">{{
            row.purchased ? '已采购' : '未采购'
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="studentName" label="学员姓名" min-width="110" />
      <el-table-column label="联系方式" min-width="160">
        <template #default="{ row }"
          ><div>手机：{{ row.studentMobile || '-' }}</div
          ><div>微信：{{ row.studentWechatId || '-' }}</div></template
        >
      </el-table-column>
      <el-table-column label="礼品" min-width="180"
        ><template #default="{ row }">{{ formatGiftPurchase(row) }}</template></el-table-column
      >
      <el-table-column
        prop="shippingAddress"
        label="邮寄地址"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="来源" min-width="210">
        <template #default="{ row }">
          <div>采购来源：{{ formatGiftPurchaseOrigin(row) }}</div>
          <div>来源人：{{ formatGiftPurchaseSourcePerson(row) }}</div>
          <div>标记已采购人：{{ formatGiftPurchaseMarker(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="生成时间" width="180"
        ><template #default="{ row }">{{ time(row.generatedAt) }}</template></el-table-column
      >
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['zsjos:gift-purchase:detail']"
            link
            type="primary"
            @click="open(row, true)"
            >详情</el-button
          >
          <el-button
            v-hasPermi="['zsjos:gift-purchase:update']"
            link
            type="primary"
            @click="open(row)"
            >编辑</el-button
          >
          <el-button
            v-hasPermi="['zsjos:gift-purchase:update']"
            link
            type="primary"
            :loading="busyId === row.id"
            @click="changeStatus(row)"
            >{{ row.purchased ? '标记未采购' : '标记已采购' }}</el-button
          >
          <el-button
            v-hasPermi="['zsjos:gift-purchase:delete']"
            link
            type="danger"
            @click="remove(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      @size-change="search"
      @current-change="load"
    />
    <el-dialog
      v-model="dialog"
      :title="readonly ? '采购详情' : form.id ? '编辑采购' : '新增采购'"
      width="min(640px, calc(100vw - 32px))"
      destroy-on-close
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <div v-loading="dialogLoading">
        <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon>
          <el-button link @click="loadDialog">重试</el-button>
        </el-alert>
        <template v-else-if="!dialogLoading">
          <el-descriptions v-if="current" :column="1" border class="mb-16px">
            <el-descriptions-item label="采购状态">{{
              current.purchased ? '已采购' : '未采购'
            }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <div>采购来源：{{ formatGiftPurchaseOrigin(current) }}</div>
              <div>来源人：{{ formatGiftPurchaseSourcePerson(current) }}</div>
              <div>标记已采购人：{{ formatGiftPurchaseMarker(current) }}</div>
              <div v-if="current.purchasedAt">标记时间：{{ time(current.purchasedAt) }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="生成时间">{{
              time(current.generatedAt)
            }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="current?.giftItemsInvalid"
            title="历史礼品信息无法读取，请核对原始记录后处理"
            type="error"
            :closable="false"
          />
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="100px"
            :disabled="readonly || saving"
          >
            <el-form-item label="学员姓名" prop="studentName"
              ><el-input v-model="form.studentName" maxlength="100"
            /></el-form-item>
            <el-form-item label="手机号"
              ><el-input v-model="form.studentMobile" maxlength="32"
            /></el-form-item>
            <el-form-item label="微信号"
              ><el-input v-model="form.studentWechatId" maxlength="64"
            /></el-form-item>
            <el-form-item label="礼品" prop="giftItemCodes">
              <span v-if="readonly">{{ current ? formatGiftPurchase(current) : '-' }}</span>
              <el-select
                v-else
                v-model="form.giftItemCodes"
                multiple
                filterable
                style="width: 100%"
                :disabled="current?.giftItemsInvalid"
                aria-label="礼品选择"
              >
                <el-option
                  v-for="gift in giftOptions"
                  :key="gift.code"
                  :value="gift.code"
                  :label="gift.name"
                  :disabled="gift.disabled"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="邮寄地址" prop="shippingAddress"
              ><el-input v-model="form.shippingAddress" type="textarea" maxlength="1000" :rows="3"
            /></el-form-item>
          </el-form>
          <template v-if="readonly">
            <h4>操作记录</h4>
            <el-empty
              v-if="!operations.length"
              description="暂无操作记录（历史记录不补造）"
              :image-size="60"
            />
            <div v-for="(operation, index) in operations" :key="index" class="operation-row">
              {{ time(operation.occurredAt) }} ·
              {{ actionLabels[operation.action] || operation.action }} ·
              {{ operation.operatorName || '历史未记录' }}
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="dialog = false">{{
          readonly ? '关闭' : '取消'
        }}</el-button>
        <el-button
          v-if="!readonly"
          type="primary"
          :loading="saving"
          :disabled="dialogLoading || !!dialogError || current?.giftItemsInvalid"
          @click="save"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import {
  getGiftPurchasePage,
  getGiftPurchase,
  getGiftPurchaseOperations,
  getGiftPurchaseOptions,
  createGiftPurchase,
  updateGiftPurchase,
  setGiftPurchased,
  deleteGiftPurchase,
  formatGiftPurchase,
  formatGiftPurchaseOrigin,
  formatGiftPurchaseSourcePerson,
  formatGiftPurchaseMarker,
  type GiftPurchaseVO,
  type GiftPurchaseSaveReq,
  type GiftPurchaseOperation,
  type GiftPurchaseOption
} from '@/api/zsjos/gift'

const loading = ref(false),
  loadError = ref(''),
  rows = ref<GiftPurchaseVO[]>([]),
  total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 20,
  studentName: '',
  orderNo: '',
  giftKeyword: '',
  purchased: false as boolean | 'all'
})
const dialog = ref(false),
  dialogLoading = ref(false),
  dialogError = ref(''),
  saving = ref(false),
  readonly = ref(false)
const busyId = ref<number>(),
  formRef = ref<FormInstance>(),
  current = ref<GiftPurchaseVO>()
const catalog = ref<GiftPurchaseOption[]>([]),
  operations = ref<GiftPurchaseOperation[]>([])
const form = reactive<GiftPurchaseSaveReq>({
  studentName: '',
  shippingAddress: '',
  giftItemCodes: []
})
let targetId: number | undefined
const rules: FormRules = {
  studentName: [{ required: true, whitespace: true, message: '请输入学员姓名', trigger: 'blur' }],
  shippingAddress: [
    { required: true, whitespace: true, message: '请输入邮寄地址', trigger: 'blur' }
  ],
  giftItemCodes: [
    { type: 'array', required: true, min: 1, message: '请选择礼品', trigger: 'change' }
  ]
}
const actionLabels = {
  CREATE: '新增采购',
  GENERATE: '订单自动生成',
  UPDATE: '编辑采购',
  PURCHASE: '标记已采购',
  UNPURCHASE: '标记未采购',
  DELETE: '删除采购'
}
const time = (value?: string | number) => (value ? formatDate(value) : '-')
const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : typeof error === 'string' ? error : fallback
const giftOptions = computed(() => {
  const parents = new Set(catalog.value.map((gift) => gift.parentId))
  const selected = new Map(
    (current.value?.giftItemSnapshots || []).map((gift) => [gift.code, gift])
  )
  const options = catalog.value
    .filter((gift) => !parents.has(gift.id) || selected.has(gift.code))
    .map((gift) => ({
      code: gift.code,
      name: selected.has(gift.code) ? selected.get(gift.code)?.name || '历史名称未记录' : gift.name,
      disabled: (gift.status !== 0 || parents.has(gift.id)) && !selected.has(gift.code)
    }))
  selected.forEach((gift) => {
    if (!options.some((option) => option.code === gift.code))
      options.push({ code: gift.code, name: gift.name || '历史名称未记录', disabled: false })
  })
  return options
})
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const result = await getGiftPurchasePage({
      ...query,
      purchased: query.purchased === 'all' ? undefined : query.purchased
    })
    if (sequence !== loadSequence) return
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    if (sequence === loadSequence) {
      rows.value = []
      total.value = 0
      loadError.value = errorText(error, '采购记录加载失败，请重试或核对访问权限')
    }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void load()
}
const reset = () => {
  Object.assign(query, { studentName: '', orderNo: '', giftKeyword: '', purchased: false })
  search()
}
const open = (row?: GiftPurchaseVO, detail = false) => {
  targetId = row?.id
  readonly.value = detail
  current.value = undefined
  operations.value = []
  Object.assign(form, {
    id: undefined,
    studentName: '',
    studentMobile: '',
    studentWechatId: '',
    shippingAddress: '',
    giftItemCodes: []
  })
  dialog.value = true
  void loadDialog()
}
let dialogSequence = 0
const loadDialog = async () => {
  const sequence = ++dialogSequence,
    id = targetId,
    detail = readonly.value
  dialogLoading.value = true
  dialogError.value = ''
  try {
    const [row, history, gifts] = await Promise.all([
      id ? getGiftPurchase(id) : undefined,
      detail && id ? getGiftPurchaseOperations(id) : [],
      detail ? [] : getGiftPurchaseOptions()
    ])
    if (sequence !== dialogSequence || !dialog.value) return
    current.value = row
    operations.value = history
    catalog.value = gifts
    if (row)
      Object.assign(form, {
        id: row.id,
        studentName: row.studentName,
        studentMobile: row.studentMobile || '',
        studentWechatId: row.studentWechatId || '',
        shippingAddress: row.shippingAddress || '',
        giftItemCodes: (row.giftItemSnapshots || []).map((gift) => gift.code)
      })
  } catch (error) {
    if (sequence === dialogSequence)
      dialogError.value = errorText(error, '采购信息加载失败，请重试或核对访问权限')
  } finally {
    if (sequence === dialogSequence) dialogLoading.value = false
  }
}
const save = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form }
    form.id ? await updateGiftPurchase(data) : await createGiftPurchase(data)
    ElMessage.success('保存成功')
    dialog.value = false
    await load()
  } catch {
    /* The shared HTTP adapter displays the backend's actionable error. */
  } finally {
    saving.value = false
  }
}
const changeStatus = async (row: GiftPurchaseVO) => {
  if (busyId.value !== undefined) return
  busyId.value = row.id
  try {
    await setGiftPurchased(row.id, !row.purchased)
    ElMessage.success('采购状态已更新')
    if (query.purchased !== 'all' && rows.value.length === 1 && query.pageNo > 1) query.pageNo--
    await load()
  } catch {
    /* Shared HTTP error feedback. */
  } finally {
    busyId.value = undefined
  }
}
const remove = async (row: GiftPurchaseVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.studentName}」的采购记录？操作记录将保留。`,
      '删除采购',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteGiftPurchase(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && query.pageNo > 1) query.pageNo--
    await load()
  } catch {
    /* Shared HTTP error feedback. */
  }
}
onMounted(load)
</script>
<style scoped>
.pagination {
  margin-top: 16px;
  overflow-x: auto;
}
.operation-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
</style>
