<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getAreaTree, getLeadCatalog, getLeadDetail, supplementLead, type LeadCatalog } from '@/api/lead'
import { useDict } from '@/composables/useDict'
import type { DictItem } from '@/stores/app'
import type { AreaNode } from '@/components/AreaPicker.vue'
import type { SelectedProduct } from '@/components/ProductPicker.vue'
import AreaPicker from '@/components/AreaPicker.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import { createIdempotencyKey } from '@/utils/idempotency'

defineOptions({ name: 'LeadSupplement' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)
const { loadLeadCategories } = useDict()

const submitting = ref(false)
const dataLoading = ref(true)
const dataError = ref('')

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

async function loadForm() {
  dataLoading.value = true
  dataError.value = ''
  try {
    const [lead, catalogData, categories, areaData] = await Promise.all([
      getLeadDetail(leadId),
      getLeadCatalog(),
      loadLeadCategories(),
      getAreaTree()
    ])
    catalog.value = catalogData
    leadCategories.value = categories
    areaTree.value = areaData
    form.area = {
      provinceCode: lead.provinceCode,
      provinceName: lead.provinceName,
      cityCode: lead.cityCode,
      cityName: lead.cityName
    }
    form.leadCategory = lead.leadCategory
    form.products = lead.intendedProducts.map(product => ({
      spuRef: product.spuRef,
      spuName: product.spuName,
      skuRef: product.skuRef,
      skuName: product.skuName,
      spuUnknown: !product.spuRef,
      skuUnknown: !product.skuRef,
      primary: product.primary
    }))
    form.remark = lead.remark || ''
  } catch {
    dataError.value = '客资资料加载失败，请重试'
  } finally {
    dataLoading.value = false
  }
}

onMounted(loadForm)

async function handleSubmit() {
  if (submitting.value) return
  if (!form.area) return showToast('请选择客户地区')
  if (!form.leadCategory) return showToast('请选择客资分类')
  if (form.products.length === 0) return showToast('请至少选择一个意向课程')
  if (!form.products.some(product => product.primary)) return showToast('请设置一个主意向课程')
  submitting.value = true
  try {
    await supplementLead(leadId, {
      provinceCode: form.area.provinceCode,
      cityCode: form.area.cityCode,
      leadCategory: form.leadCategory,
      intendedProducts: form.products.map(p => ({
            spuRef: p.spuUnknown ? undefined : p.spuRef,
            skuRef: p.skuRef,
            spuUnknown: p.spuUnknown,
            skuUnknown: p.skuUnknown,
            primary: p.primary
          })),
      remark: form.remark.trim() || undefined,
      idempotencyKey: createIdempotencyKey()
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
      <van-empty v-if="dataError" :description="dataError" image="error">
        <van-button size="small" type="primary" @click="loadForm">重新加载</van-button>
      </van-empty>
      <template v-else>
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
      </template>
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
