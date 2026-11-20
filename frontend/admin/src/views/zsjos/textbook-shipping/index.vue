<template>
  <ContentWrap title="教材邮寄">
    <el-form inline @submit.prevent="search">
      <el-form-item label="查询订单">
        <el-input v-model="keyword" clearable placeholder="订单号、姓名、手机或微信" />
      </el-form-item>
      <el-button native-type="submit" type="primary">查询</el-button>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <el-table v-loading="loading" :data="rows" border empty-text="暂无教材邮寄记录">
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.products" size="small">
            <el-table-column prop="name" label="商品" />
            <el-table-column prop="sku" label="规格" />
            <el-table-column prop="quantity" label="数量" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="studentName" label="学员姓名" min-width="100" />
      <el-table-column label="联系方式" min-width="160">
        <template #default="{ row }">
          <div>报名姓名：{{ row.destination?.name || '—' }}</div>
          <div>报名手机：{{ row.destination?.mobile || '—' }}</div>
          <div>订单微信：{{ row.wechat || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="教材邮寄地址" min-width="220">
        <template #default="{ row }">{{ row.destination?.address || '待补地址' }}</template>
      </el-table-column>
      <el-table-column label="邮寄信息" min-width="220">
        <template #default="{ row }">
          <div v-for="product in row.products" :key="product.itemId" class="shipment">
            <span v-if="row.products.length > 1">{{ product.name }} {{ product.sku }} · </span>
            <span>{{ product.shipment ? product.shipment.carrier + ' ' + product.shipment.trackingNo : '待填写' }}</span>
            <el-button v-hasPermi="['zsjos:textbook-shipping:update']" link type="primary" @click="edit(row, product)">
              {{ product.shipment ? '修改邮寄信息' : '填写邮寄信息' }}
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="来源订单" min-width="170" />
      <el-table-column label="订单生效时间" min-width="175">
        <template #default="{ row }">{{ row.effectiveAt ? formatDate(new Date(row.effectiveAt)) : '—' }}</template>
      </el-table-column>
    </el-table>
    <div>共 {{ total }} 笔订单</div>
    <el-pagination v-model:current-page="query.pageNo" :page-size="query.pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
    <el-dialog v-model="editing" title="填写邮寄信息" width="min(460px, 94vw)" :close-on-click-modal="false" :before-close="closeEditor">
      <p>{{ selected?.orderNo }} · {{ selected?.product.name }} {{ selected?.product.sku }}</p>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="快递公司" required><el-input v-model="draft.carrier" maxlength="100" :disabled="saving" /></el-form-item>
        <el-form-item label="运单号" required><el-input v-model="draft.trackingNo" maxlength="100" :disabled="saving" /></el-form-item>
      </el-form>
      <el-alert v-if="saveError" type="error" :title="saveError" :closable="false" />
      <template #footer>
        <el-button :disabled="saving" @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!draft.carrier.trim() || !draft.trackingNo.trim()" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { getTextbookAddresses, saveShipping, type TextbookProduct, type TextbookAddressRow } from '@/api/zsjos/textbook-shipping'

defineOptions({ name: 'ZsjosTextbookShipping' })

const keyword = ref('')
const query = reactive({ pageNo: 1, pageSize: 20, keyword: '' })
const rows = ref<TextbookAddressRow[]>([])
const total = ref(0)
const error = ref('')
const loading = ref(false)
let sequence = 0

async function load() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const page = await getTextbookAddresses({ ...query })
    if (current !== sequence) return
    rows.value = page.list
    total.value = page.total
  } catch (cause) {
    if (current !== sequence) return
    rows.value = []
    total.value = 0
    error.value = cause instanceof Error ? cause.message : String(cause || '教材地址加载失败')
  } finally {
    if (current === sequence) loading.value = false
  }
}
function search() {
  query.keyword = keyword.value.trim()
  query.pageNo = 1
  load()
}
const editing = ref(false)
const saving = ref(false)
const saveError = ref('')
const selected = ref<{ orderId: number; orderNo: string; product: TextbookProduct }>()
const draft = reactive({ carrier: '', trackingNo: '', version: 0 })
function edit(row: TextbookAddressRow, product: TextbookProduct) {
  selected.value = { orderId: row.orderId, orderNo: row.orderNo, product }
  Object.assign(draft, product.shipment || { carrier: '', trackingNo: '', version: 0 })
  saveError.value = ''
  editing.value = true
}
function closeEditor(done: () => void) { if (!saving.value) done() }
async function save() {
  if (saving.value || !selected.value || !draft.carrier.trim() || !draft.trackingNo.trim()) return
  saving.value = true
  saveError.value = ''
  try {
    await saveShipping(selected.value.orderId, selected.value.product.itemId, {
      carrier: draft.carrier.trim(), trackingNo: draft.trackingNo.trim(), version: draft.version
    })
    editing.value = false
    await load()
  } catch (cause) {
    saveError.value = cause instanceof Error ? cause.message : String(cause || '保存失败')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.shipment { overflow-wrap: anywhere; }
.shipment + .shipment { margin-top: 6px; }
</style>
