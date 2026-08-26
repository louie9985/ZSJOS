<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SpuItem, SkuItem, CategoryNode } from '@/api/lead'

/**
 * 意向课程选择器
 * 支持从目录中搜索、选择课程，标记主意向
 */

export interface SelectedProduct {
  spuRef: string
  spuName: string
  skuRef?: string
  skuName?: string
  attrValues?: Record<string, string>
  price?: number
  spuUnknown: boolean
  skuUnknown: boolean
  primary: boolean
}

const props = defineProps<{
  modelValue: SelectedProduct[]
  spus: SpuItem[]
  skus: SkuItem[]
  categoryTree: CategoryNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SelectedProduct[]]
}>()

const show = ref(false)
const searchText = ref('')

// 已选的 spuRef 集合
const selectedRefs = computed(() => new Set(props.modelValue.map(p => p.spuRef)))
const selectedCategory = ref<number | undefined>()
const selectedSpu = ref<SpuItem>()
const selectedSku = ref<SkuItem>()
const selectedAttrs = ref<Record<string, string>>({})

function flattenCategories(nodes: CategoryNode[], level = 0): Array<CategoryNode & { level: number }> {
  return nodes.flatMap(node => [{ ...node, level }, ...flattenCategories(node.children || [], level + 1)])
}

const categoryOptions = computed(() => flattenCategories(props.categoryTree))

const visibleSpus = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  return props.spus.filter(spu => {
    const matchesCategory = selectedCategory.value == null || spu.categoryPath.some(category => category.id === selectedCategory.value)
    const matchesKeyword = !keyword || spu.spuName.toLowerCase().includes(keyword) || spu.categoryName.toLowerCase().includes(keyword)
    return matchesCategory && matchesKeyword
  })
})

const selectedSpuSkus = computed(() => selectedSpu.value ? props.skus.filter(sku => {
  if (sku.spuRef !== selectedSpu.value?.spuRef) return false
  return Object.entries(selectedAttrs.value).every(([key, value]) => sku.attrValues[key] === value)
}) : [])
const requiredAttrsSelected = computed(() => selectedSpu.value?.attrs
  .filter(attr => attr.required)
  .every(attr => Boolean(selectedAttrs.value[attr.attrKey])) ?? false)

function selectSpu(spu: SpuItem) {
  if (selectedRefs.value.has(spu.spuRef)) return
  selectedSpu.value = spu
  selectedSku.value = undefined
  selectedAttrs.value = {}
}

function selectAttr(attrKey: string, value: string) {
  selectedAttrs.value = { ...selectedAttrs.value, [attrKey]: value }
  if (selectedSku.value && selectedSku.value.attrValues[attrKey] !== value) selectedSku.value = undefined
}

function confirmSpu() {
  const spu = selectedSpu.value
  if (!spu) return
  const sku = selectedSku.value

  const newItem: SelectedProduct = {
    spuRef: spu.spuRef,
    spuName: spu.spuName,
    skuRef: sku?.skuRef,
    skuName: sku?.skuName,
    attrValues: sku?.attrValues,
    price: sku?.price,
    spuUnknown: false,
    skuUnknown: !sku,
    primary: props.modelValue.length === 0 // 第一个默认为主意向
  }
  emit('update:modelValue', [...props.modelValue, newItem])
  selectedSpu.value = undefined
  selectedSku.value = undefined
  selectedAttrs.value = {}
  searchText.value = ''
  show.value = false
}

function removeProduct(index: number) {
  const updated = [...props.modelValue]
  const removed = updated.splice(index, 1)[0]
  // 如果删除的是主意向，把第一个设为主意向
  if (removed.primary && updated.length > 0) {
    updated[0].primary = true
  }
  emit('update:modelValue', updated)
}

function setPrimary(index: number) {
  const updated = props.modelValue.map((item, i) => ({
    ...item,
    primary: i === index
  }))
  emit('update:modelValue', updated)
}

function addUnknown() {
  const newItem: SelectedProduct = {
    spuRef: `unknown_${Date.now()}`,
    spuName: '未明确课程',
    skuRef: undefined,
    skuName: undefined,
    spuUnknown: true,
    skuUnknown: true,
    primary: props.modelValue.length === 0
  }
  emit('update:modelValue', [...props.modelValue, newItem])
}
</script>

<template>
  <div class="product-picker">
    <!-- 已选列表 -->
    <div v-if="modelValue.length > 0" class="product-picker__selected">
      <div
        v-for="(item, index) in modelValue"
        :key="item.spuRef"
        class="product-picker__item"
      >
        <div class="product-picker__item-info">
          <van-tag
            v-if="item.primary"
            type="primary"
            size="medium"
            style="margin-right: 6px;"
          >
            主意向
          </van-tag>
          <span class="product-picker__item-name">{{ item.spuName }}</span>
          <span v-if="item.skuName" class="product-picker__item-meta">{{ item.skuName }}<template v-if="item.price != null"> · ¥{{ item.price }}</template></span>
        </div>
        <div class="product-picker__item-actions">
          <van-button
            v-if="!item.primary"
            size="mini"
            plain
            @click="setPrimary(index)"
          >
            设为主
          </van-button>
          <van-icon name="cross" size="16" color="var(--h5-text-placeholder)" @click="removeProduct(index)" />
        </div>
      </div>
    </div>

    <!-- 添加按钮 -->
    <div class="product-picker__add">
      <van-button icon="plus" size="small" plain round @click="show = true">
        选择课程
      </van-button>
      <van-button size="small" plain round @click="addUnknown">
        未明确课程
      </van-button>
    </div>

    <!-- 课程选择弹窗 -->
    <van-popup v-model:show="show" position="bottom" round :style="{ height: '70%' }">
      <div class="product-picker__popup">
        <div class="product-picker__popup-header">
          <span>选择意向课程</span>
          <van-icon name="cross" size="20" @click="show = false" />
        </div>
        <van-search v-model="searchText" placeholder="搜索课程名称" shape="round" />
        <div v-if="categoryTree.length" class="product-picker__categories">
          <van-button size="mini" plain :type="selectedCategory == null ? 'primary' : 'default'" @click="selectedCategory = undefined">全部</van-button>
          <van-button v-for="category in categoryOptions" :key="category.id" size="mini" plain :type="selectedCategory === category.id ? 'primary' : 'default'" :style="{ marginLeft: `${category.level * 8}px` }" @click="selectedCategory = category.id">{{ category.name }}</van-button>
        </div>
        <div v-if="selectedSpu" class="product-picker__sku-panel">
          <div class="product-picker__sku-title">选择 {{ selectedSpu.spuName }} 的规格</div>
          <div v-for="attr in selectedSpu.attrs" :key="attr.attrKey" class="product-picker__attr">
            <div class="product-picker__attr-name">{{ attr.attrName }}<span v-if="attr.required">*</span></div>
            <div class="product-picker__attr-values"><van-button v-for="option in attr.values" :key="option.value" size="mini" plain :type="selectedAttrs[attr.attrKey] === option.value ? 'primary' : 'default'" @click="selectAttr(attr.attrKey, option.value)">{{ option.label }}</van-button></div>
          </div>
          <van-cell v-for="sku in selectedSpuSkus" :key="sku.skuRef" :title="sku.skuName" :label="sku.attrValues && Object.values(sku.attrValues).join(' / ')" clickable @click="selectedSku = sku">
            <template #value><span v-if="sku.price != null">¥{{ sku.price }}</span><van-icon v-if="selectedSku?.skuRef === sku.skuRef" name="success" color="var(--h5-primary)" /></template>
          </van-cell>
          <van-button block type="primary" size="small" :disabled="!requiredAttrsSelected || (selectedSpuSkus.length > 0 && !selectedSku)" @click="confirmSpu">确认选择</van-button>
        </div>
        <div class="product-picker__popup-list">
          <van-cell
            v-for="spu in visibleSpus"
            :key="spu.spuRef"
            :title="spu.spuName"
            :label="spu.categoryName"
            clickable
            @click="selectSpu(spu)"
          >
            <template #right-icon>
              <van-icon
                v-if="selectedRefs.has(spu.spuRef)"
                name="success"
                color="var(--h5-primary)"
              />
              <van-icon v-else name="add-o" color="var(--h5-text-placeholder)" />
            </template>
          </van-cell>
          <van-empty v-if="visibleSpus.length === 0" description="无匹配课程" image="search" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.product-picker__selected {
  margin-bottom: 12px;
}
.product-picker__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--h5-primary-opacity);
  border-radius: 8px;
  margin-bottom: 8px;
}
.product-picker__item-info {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.product-picker__item-name {
  font-size: 14px;
  color: var(--h5-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__item-meta{margin-left:6px;color:var(--h5-text-secondary);font-size:11px;white-space:nowrap}
.product-picker__item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 8px;
}

.product-picker__add {
  display: flex;
  gap: 10px;
}

.product-picker__popup {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.product-picker__popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  font-size: 16px;
  font-weight: 500;
}
.product-picker__popup-list {
  flex: 1;
  overflow-y: auto;
}
.product-picker__attr{padding:8px 0}.product-picker__attr-name{margin-bottom:6px;font-size:12px;color:var(--h5-text-secondary)}.product-picker__attr-name span{color:var(--h5-danger)}.product-picker__attr-values{display:flex;flex-wrap:wrap;gap:6px}
</style>
