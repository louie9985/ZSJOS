<template>
  <div v-loading="loading">
    <!-- 概览指标 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :xs="24" :sm="12">
        <el-card shadow="never">
          <div class="text-sm text-gray-500">资产总数</div>
          <div class="mt-1 text-3xl font-semibold">{{ data.totalCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="never">
          <div class="text-sm text-gray-500">资产原值合计</div>
          <div class="mt-1 text-3xl font-semibold">
            ¥{{ formatMoney(data.totalOriginalValue) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" header="按状态分布">
          <StatList :items="data.statusStats ?? []" :total="data.totalCount ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" header="按分类分布">
          <StatList :items="data.categoryStats ?? []" :total="data.totalCount ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" header="按使用部门分布">
          <StatList :items="data.deptStats ?? []" :total="data.totalCount ?? 0" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import * as StatisticsApi from '@/api/eam/statistics'
import StatList from './StatList.vue'

defineOptions({ name: 'EamStatistics' })

const loading = ref(false)
const data = ref<Partial<StatisticsApi.StatisticsVO>>({})

const formatMoney = (value?: number) => {
  if (value == null) {
    return '0.00'
  }
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const getData = async () => {
  loading.value = true
  try {
    data.value = await StatisticsApi.getStatistics()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getData()
})
</script>
