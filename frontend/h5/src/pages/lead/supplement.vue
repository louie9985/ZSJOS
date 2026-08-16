<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getLeadCatalog, supplementLead, type LeadCatalog } from '@/api/lead'
import { useDict } from '@/composables/useDict'
import type { DictItem } from '@/stores/app'
import type { AreaNode } from '@/components/AreaPicker.vue'
import type { SelectedProduct } from '@/components/ProductPicker.vue'
import AreaPicker from '@/components/AreaPicker.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import request from '@/api/request'

defineOptions({ name: 'LeadSupplement' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)
const { loadLeadCategories } = useDict()

const submitting = ref(false)
const dataLoading = ref(true)

const form = reactive({
  area: undefined as { provinceCode: string; provinceName: string; cityCode: string; cityName: string } | undefined,
  leadCategory: '',
  products: [] as SelectedProduct[],
  remark: ''
})

const areaTree = ref<AreaNode[]>([])
const catalog = ref<LeadCatalog>({ categoryTree: [], spus: [], skus: [] })
const leadCategories = ref<DictItem[]>([])
const showCategoryPicker = ref(false)

onMounted(async () => {
  try {
    const [catalogData, categories, areaData] = await Promise.all([
      getLeadCatalog(),
      loadLeadCategories(),
      request.get<never, AreaNode[]>('/zsjos/lead/area-tree').catch(() => [] as AreaNode[])
    ])
    catalog.value = catalogData
    leadCategories.value = categories
    areaTree.value = areaData
  } finally {
    dataLoading.value = false
  }
})

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  try {
    await supplementLead(leadId, {
      provinceCode: form.area?.provinceCode,
      cityCode: form.area?.cityCode,
      leadCategory: form.leadCategory || undefined,
      intendedProducts: form.products.length > 0
        ? form.products.map(p => ({
            spuRef: p.spuUnknown ? '' : p.spuRef,
            skuRef: p.skuRef,
            spuUnknown: p.spuUnknown,
            skuUnknown: p.skuUnknown,
            primary: p.primary
          }))
        : undefined,
      remark: form.remark.trim() || undefined,
      idempotencyKey: crypto.randomUUID()
    })
    showSuccessToast('补充成功')
    router.back()
  } catch {
    // 拦截器已处理
  } finally {
    submitting.value = false
  }
}

const categoryLabel = () => leadCategories.value.find(c => c.value === form.leadCategory)?.label || ''
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="补充客资" left-arrow @click-left="$router.back()" />

    <van-skeleton :loading="dataLoading" :row="6" style="padding: 16px;">
      <div class="card">
        <p style="font-size: 13px; color: var(--h5-text-secondary); margin-bottom: 12px;">
          补充或更新客资的地区、分类、意向课程、备注（不能修改姓名和联系方式）
        </p>

        <div class="field-label">客户地区</div>
        <AreaPicker v-model="form.area" :area-tree="areaTree" />

        <van-field
          :model-value="categoryLabel()"
          label="客资分类"
          placeholder="请选择"
          readonly
          clickable
          right-icon="arrow-down"
          @click="showCategoryPicker = true"
        />

        <div class="field-label">意向课程</div>
        <ProductPicker
          v-model="form.products"
          :spus="catalog.spus"
          :skus="catalog.skus"
          :category-tree="catalog.categoryTree"
        />

        <van-field
          v-model="form.remark"
          label="备注"
          type="textarea"
          placeholder="补充备注信息"
          maxlength="1000"
          show-word-limit
          rows="3"
          autosize
        />
      </div>

      <div style="padding: 16px;">
        <van-button type="primary" block round :loading="submitting" @click="handleSubmit">
          提交补充
        </van-button>
      </div>

      <van-popup v-model:show="showCategoryPicker" position="bottom" round>
        <van-picker
          :columns="leadCategories.map(c => ({ text: c.label, value: c.value }))"
          @confirm="({ selectedValues }) => { form.leadCategory = selectedValues[0] as string; showCategoryPicker = false }"
          @cancel="showCategoryPicker = false"
        />
      </van-popup>
    </van-skeleton>
  </div>
</template>

<style scoped>
.field-label {
  font-size: 14px;
  color: var(--h5-text-primary);
  padding: 12px 16px 6px;
  font-weight: 500;
}
</style>
