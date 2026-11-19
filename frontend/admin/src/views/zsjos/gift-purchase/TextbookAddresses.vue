<template>
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
    <el-table-column prop="orderNo" label="来源订单" min-width="170" />
    <el-table-column label="订单生效时间" min-width="175">
      <template #default="{ row }">{{ row.effectiveAt ? formatDate(new Date(row.effectiveAt)) : '—' }}</template>
    </el-table-column>
  </el-table>
  <div>共 {{ total }} 笔订单</div>
  <el-pagination v-model:current-page="query.pageNo" :page-size="query.pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { getTextbookAddresses, type TextbookAddressRow } from '@/api/zsjos/gift/textbook'

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
onMounted(load)
</script>
