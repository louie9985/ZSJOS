<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SpuItem, SkuItem, CategoryNode } from '@/api/lead'
import ProductSpecs from './ProductSpecs.vue'
import { catalogSpecs, specText, type ProductSpec } from '../utils/productSpecs'

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
  specs?: ProductSpec[]
  selectedAttrValues?: string
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
const hasUnknownProduct = computed(() => props.modelValue.some(product => product.spuUnknown))
const hasCatalogProduct = computed(() => props.modelValue.some(product => !product.spuUnknown))
const categoryPath = ref<CategoryNode[]>([])
const selectedCategoryLeaf = ref<CategoryNode>()
const selectedSpu = ref<SpuItem>()
const selectedSku = ref<SkuItem>()
const selectedAttrs = ref<Record<string, string>>({})
const skuUnknown = ref(true)

const currentCategory = computed(() => categoryPath.value[categoryPath.value.length - 1])
const currentCategoryOptions = computed(() => currentCategory.value?.children || props.categoryTree)

const visibleSpus = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword && !selectedCategoryLeaf.value) return []
  return props.spus.filter(spu => {
    const matchesCategory = keyword || !selectedCategoryLeaf.value
      ? true
      : spu.categoryPath.some(category => category.id === selectedCategoryLeaf.value?.id)
    const matchesKeyword = !keyword || spu.spuName.toLowerCase().includes(keyword) || spu.categoryName.toLowerCase().includes(keyword)
    return matchesCategory && matchesKeyword
  })
})

const selectedSpuAllSkus = computed(() => selectedSpu.value
  ? props.skus.filter(sku => sku.spuRef === selectedSpu.value?.spuRef)
  : [])
const selectedSpuSkus = computed(() => selectedSpuAllSkus.value.filter(sku => (
  Object.entries(selectedAttrs.value).every(([key, value]) => sku.attrValues[key] === value)
)))
const requiredAttrsSelected = computed(() => selectedSpu.value?.attrs
  .filter(attr => attr.required)
  .every(attr => Boolean(selectedAttrs.value[attr.attrKey])) ?? false)
const missingRequiredAttrs = computed(() => selectedSpu.value?.attrs
  .filter(attr => attr.required && !selectedAttrs.value[attr.attrKey]) || [])
const popupTitle = computed(() => selectedSpu.value ? '完善课程规格' : '选择意向课程')
const selectedSpuCategory = computed(() => selectedSpu.value?.categoryPath.map(item => item.name).join(' / ')
  || selectedSpu.value?.categoryName
  || '')
const canConfirmSpu = computed(() => {
  if (!selectedSpu.value) return false
  if (selectedSpuAllSkus.value.length === 0 || skuUnknown.value) return true
  return requiredAttrsSelected.value && selectedSpuSkus.value.length > 0 && Boolean(selectedSku.value)
})
const skuStatusText = computed(() => {
  if (!selectedSpu.value) return ''
  if (selectedSpuAllSkus.value.length === 0) return '该课程未配置具体班型，可直接确认'
  if (skuUnknown.value) return '已选择课程，具体班次/方案可稍后确认'
  if (missingRequiredAttrs.value.length > 0) {
    return `还需选择：${missingRequiredAttrs.value.map(attr => attr.attrName).join('、')}`
  }
  if (selectedSpuSkus.value.length === 0) return '当前规格暂无可选班型'
  if (selectedSku.value) return `已匹配：${selectedSku.value.skuName}`
  return `请选择一个匹配班型（${selectedSpuSkus.value.length} 个可选）`
})

function attrsFromSku(sku: SkuItem) {
  return Object.fromEntries(
    selectedSpu.value?.attrs
      .filter(attr => sku.attrValues[attr.attrKey] != null)
      .map(attr => [attr.attrKey, sku.attrValues[attr.attrKey]]) || []
  )
}

function syncSkuSelection() {
  if (skuUnknown.value) {
    selectedSku.value = undefined
    return
  }
  if (!requiredAttrsSelected.value) {
    selectedSku.value = undefined
    return
  }
  if (selectedSpuSkus.value.length === 1) {
    const sku = selectedSpuSkus.value[0]
    selectedAttrs.value = attrsFromSku(sku)
    selectedSku.value = sku
    return
  }
  selectedSku.value = undefined
}

function isAttrOptionDisabled(attrKey: string, value: string) {
  if (selectedSpuAllSkus.value.length === 0) return false
  const selections = { ...selectedAttrs.value, [attrKey]: value }
  return !selectedSpuAllSkus.value.some(sku => Object.entries(selections).every(
    ([selectedKey, selectedValue]) => sku.attrValues[selectedKey] === selectedValue
  ))
}

function selectSpu(spu: SpuItem) {
  if (selectedRefs.value.has(spu.spuRef)) return
  selectedSpu.value = spu
  selectedSku.value = undefined
  selectedAttrs.value = {}
  skuUnknown.value = true
  syncSkuSelection()
}

function enterCategory(category: CategoryNode) {
  selectedSpu.value = undefined
  selectedSku.value = undefined
  selectedAttrs.value = {}
  if (category.children?.length) {
    categoryPath.value = [...categoryPath.value, category]
    selectedCategoryLeaf.value = undefined
    return
  }
  selectedCategoryLeaf.value = category
}

function goBackCategory() {
  if (selectedSpu.value) {
    selectedSpu.value = undefined
    selectedSku.value = undefined
    selectedAttrs.value = {}
    skuUnknown.value = false
    return
  }
  if (selectedCategoryLeaf.value) {
    selectedCategoryLeaf.value = undefined
    return
  }
  categoryPath.value = categoryPath.value.slice(0, -1)
}

function resetNavigation() {
  searchText.value = ''
  categoryPath.value = []
  selectedCategoryLeaf.value = undefined
  selectedSpu.value = undefined
  selectedSku.value = undefined
  selectedAttrs.value = {}
  skuUnknown.value = false
}

function openPicker() {
  resetNavigation()
  show.value = true
}

function selectAttr(attrKey: string, value: string) {
  const nextAttrs = { ...selectedAttrs.value }
  if (nextAttrs[attrKey] === value) delete nextAttrs[attrKey]
  else nextAttrs[attrKey] = value
  selectedAttrs.value = nextAttrs
  selectedSku.value = undefined
  syncSkuSelection()
}

function selectSku(sku: SkuItem) {
  selectedAttrs.value = attrsFromSku(sku)
  selectedSku.value = sku
}

function setSkuUnknown(value: boolean) {
  skuUnknown.value = value
  selectedSku.value = undefined
  selectedAttrs.value = {}
  if (!value) syncSkuSelection()
}

function skuAttrSummary(sku: SkuItem) {
  return (sku.specs ?? catalogSpecs(sku.attrValues, selectedSpu.value?.attrs)).map(specText).join(' · ')
}

function formatPrice(price?: number) {
  return price == null ? '' : `¥${price.toFixed(2)}`
}

function confirmSpu() {
  const spu = selectedSpu.value
  if (!spu || !canConfirmSpu.value) return
  const sku = selectedSku.value
  const hasCatalogSku = selectedSpuAllSkus.value.length > 0
  const hasUnknownSku = skuUnknown.value || !hasCatalogSku

  const newItem: SelectedProduct = {
    spuRef: spu.spuRef,
    spuName: spu.spuName,
    skuRef: hasUnknownSku ? undefined : sku?.skuRef,
    skuName: hasUnknownSku ? '未明确具体班次/方案' : sku?.skuName,
    attrValues: hasUnknownSku ? undefined : sku?.attrValues,
    specs: hasUnknownSku ? [] : sku ? sku.specs ?? catalogSpecs(sku.attrValues, spu.attrs) : [],
    price: hasUnknownSku ? undefined : sku?.price,
    spuUnknown: false,
    skuUnknown: hasUnknownSku,
    primary: props.modelValue.length === 0 // 第一个默认为主意向
  }
  emit('update:modelValue', [...props.modelValue, newItem])
  selectedSpu.value = undefined
  selectedSku.value = undefined
  selectedAttrs.value = {}
  searchText.value = ''
  resetNavigation()
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
  if (hasUnknownProduct.value) return
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
          <div class="product-picker__item-title">
            <span v-if="item.primary" class="product-picker__primary-tag">主意向</span>
            <strong class="product-picker__item-name">{{ item.spuName }}</strong>
          </div>
          <ProductSpecs :product="item" />
          <span class="product-picker__item-meta">
            {{ item.skuName || (item.spuUnknown ? '课程待确认' : '班次/方案待确认') }}
            <template v-if="item.price != null"> · {{ formatPrice(item.price) }}</template>
          </span>
        </div>
        <div class="product-picker__item-actions">
          <button
            v-if="!item.primary"
            type="button"
            class="product-picker__set-primary"
            @click="setPrimary(index)"
          >
            设为主意向
          </button>
          <button
            type="button"
            class="product-picker__remove"
            :aria-label="`删除意向课程：${item.spuName}`"
            title="删除意向课程"
            @click="removeProduct(index)"
          >
            <van-icon name="delete-o" size="18" />
          </button>
        </div>
      </div>
    </div>

    <!-- 添加按钮 -->
    <div class="product-picker__add">
      <button
        type="button"
        class="product-picker__entry"
        :class="{ 'is-active': hasCatalogProduct }"
        @click="openPicker"
      >
        <span class="product-picker__entry-icon"><van-icon name="apps-o" size="21" /></span>
        <span class="product-picker__entry-copy">
          <strong>选择课程</strong>
          <small>从课程目录中选择</small>
        </span>
      </button>
      <button
        type="button"
        class="product-picker__entry"
        :class="{ 'is-active': hasUnknownProduct }"
        :disabled="hasUnknownProduct"
        @click="addUnknown"
      >
        <span class="product-picker__entry-icon"><van-icon name="question-o" size="21" /></span>
        <span class="product-picker__entry-copy">
          <strong>未明确课程</strong>
          <small>{{ hasUnknownProduct ? '已添加' : '稍后再确认课程' }}</small>
        </span>
      </button>
    </div>

    <!-- 课程选择弹窗 -->
    <van-popup
      v-model:show="show"
      position="bottom"
      round
      teleport="body"
      safe-area-inset-bottom
      class="product-picker__sheet norem"
      overlay-class="submit-picker-overlay"
    >
      <div class="product-picker__popup">
        <div class="product-picker__popup-header">
          <span>{{ popupTitle }}</span>
          <button type="button" aria-label="关闭课程选择" @click="show = false">
            <van-icon name="cross" size="20" />
          </button>
        </div>
        <van-search v-if="!selectedSpu" v-model="searchText" class="product-picker__search" placeholder="搜索课程名称" shape="round" />
        <div v-if="!selectedSpu && !searchText" class="product-picker__navigation">
          <button v-if="categoryPath.length || selectedCategoryLeaf" type="button" class="product-picker__back" @click="goBackCategory">
            <van-icon name="arrow-left" size="16" />
            <span>返回</span>
          </button>
          <span class="product-picker__breadcrumb">{{ [...categoryPath, ...(selectedCategoryLeaf ? [selectedCategoryLeaf] : [])].map(item => item.name).join(' / ') || '选择课程分类' }}</span>
        </div>
        <div v-if="selectedSpu" class="product-picker__sku-panel">
          <div class="product-picker__sku-scroll">
            <button type="button" class="product-picker__course-back" @click="goBackCategory">
              <van-icon name="arrow-left" size="16" />
              <span>返回课程列表</span>
            </button>

            <div class="product-picker__course-card">
              <div class="product-picker__course-icon">
                <van-icon name="apps-o" size="22" />
              </div>
              <div class="product-picker__course-info">
                <span>已选课程</span>
                <strong>{{ selectedSpu.spuName }}</strong>
                <small v-if="selectedSpuCategory">{{ selectedSpuCategory }}</small>
              </div>
              <van-icon name="success" size="20" color="var(--h5-primary)" />
            </div>

            <div v-if="selectedSpuAllSkus.length" class="product-picker__specification">
              <div class="product-picker__section-heading">
                <strong>选择课程规格</strong>
                <span v-if="!skuUnknown">带 * 为必选项</span>
              </div>
              <div class="product-picker__unknown-sku norem">
                <van-checkbox
                  :model-value="skuUnknown"
                  shape="square"
                  icon-size="20"
                  @update:model-value="setSkuUnknown"
                >
                  未明确具体班次/方案
                </van-checkbox>
              </div>
              <template v-if="!skuUnknown">
                <div v-for="attr in selectedSpu.attrs" :key="attr.attrKey" class="product-picker__attr">
                  <div class="product-picker__attr-name">
                    <span :class="{ 'h5-required-label': attr.required }">{{ attr.attrName }}</span>
                  </div>
                  <div class="product-picker__attr-values">
                    <button
                      v-for="option in attr.values"
                      :key="option.value"
                      type="button"
                      class="product-picker__attr-option"
                      :class="{ 'is-active': selectedAttrs[attr.attrKey] === option.value }"
                      :disabled="isAttrOptionDisabled(attr.attrKey, option.value)"
                      :aria-pressed="selectedAttrs[attr.attrKey] === option.value"
                      @click="selectAttr(attr.attrKey, option.value)"
                    >
                      <span>{{ option.label }}</span>
                      <van-icon v-if="selectedAttrs[attr.attrKey] === option.value" name="success" size="14" />
                    </button>
                  </div>
                </div>
              </template>
            </div>

            <div v-if="selectedSpuAllSkus.length === 0" class="product-picker__notice product-picker__notice--info">
              <van-icon name="info-o" size="18" />
              <span>该课程未配置具体班型，可直接确认选择</span>
            </div>

            <div v-else-if="!skuUnknown && requiredAttrsSelected && selectedSpuSkus.length" class="product-picker__sku-section">
              <div class="product-picker__section-heading">
                <strong>匹配班型</strong>
                <span>{{ selectedSpuSkus.length }} 个可选</span>
              </div>
              <button
                v-for="sku in selectedSpuSkus"
                :key="sku.skuRef"
                type="button"
                class="product-picker__sku-option"
                :class="{ 'is-active': selectedSku?.skuRef === sku.skuRef }"
                :aria-pressed="selectedSku?.skuRef === sku.skuRef"
                @click="selectSku(sku)"
              >
                <div class="product-picker__sku-info">
                  <strong>{{ sku.skuName }}</strong>
                  <span v-if="skuAttrSummary(sku)">{{ skuAttrSummary(sku) }}</span>
                </div>
                <div class="product-picker__sku-value">
                  <b v-if="sku.price != null">{{ formatPrice(sku.price) }}</b>
                  <span class="product-picker__radio">
                    <van-icon v-if="selectedSku?.skuRef === sku.skuRef" name="success" size="14" />
                  </span>
                </div>
              </button>
            </div>

            <div v-if="!skuUnknown && selectedSpuAllSkus.length > 0 && requiredAttrsSelected && selectedSpuSkus.length === 0" class="product-picker__notice product-picker__notice--error">
              <van-icon name="warning-o" size="18" />
              <span>当前规格暂无可选班型，请重新选择规格</span>
            </div>
          </div>

          <div class="product-picker__actions">
            <div class="product-picker__status" :class="{ 'is-complete': canConfirmSpu, 'is-error': !skuUnknown && requiredAttrsSelected && selectedSpuSkus.length === 0 && selectedSpuAllSkus.length > 0 }">
              <van-icon :name="canConfirmSpu ? 'passed' : 'info-o'" size="16" />
              <span>{{ skuStatusText }}</span>
            </div>
            <van-button block round type="primary" size="large" :disabled="!canConfirmSpu" @click="confirmSpu">
              确认选择
            </van-button>
          </div>
        </div>
        <div v-if="!selectedSpu && !searchText && !selectedCategoryLeaf" class="product-picker__popup-list">
          <van-cell
            v-for="category in currentCategoryOptions"
            :key="category.id"
            :title="category.name"
            is-link
            clickable
            @click="enterCategory(category)"
          />
          <van-empty v-if="currentCategoryOptions.length === 0" description="暂无课程分类" image="search" />
        </div>
        <div v-else-if="!selectedSpu" class="product-picker__popup-list">
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
          <van-empty v-if="visibleSpus.length === 0" :description="searchText ? '无匹配课程' : '该分类暂无课程'" image="search" />
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
  gap: 12px;
  min-height: 74px;
  padding: 12px;
  border: 1px solid var(--h5-glass-border);
  background: var(--h5-glass-surface);
  border-radius: 10px;
  margin-bottom: 8px;
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 46%, transparent);
}
.product-picker__item-info {
  display: flex;
  align-items: flex-start;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.product-picker__item-title {
  display: flex;
  max-width: 100%;
  min-width: 0;
  align-items: flex-start;
  gap: 6px;
}
.product-picker__primary-tag {
  flex: 0 0 auto;
  padding: 3px 7px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 26%, transparent);
  border-radius: 7px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 10px;
  font-weight: 600;
  line-height: 15px;
}
.product-picker__item-name {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--h5-text-primary);
  line-height: 20px;
  overflow-wrap: anywhere;
}
.product-picker__item-meta {
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 16px;
  overflow-wrap: anywhere;
}
.product-picker__item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 8px;
}
.product-picker__set-primary,
.product-picker__remove {
  border: 1px solid var(--h5-glass-border);
  border-radius: 8px;
  background: var(--h5-glass-sunken);
  color: var(--h5-primary);
  font: inherit;
}
.product-picker__set-primary {
  min-height: 32px;
  padding: 0 8px;
  font-size: 11px;
  white-space: nowrap;
}
.product-picker__remove {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: var(--h5-text-placeholder);
}
.product-picker__remove:active,
.product-picker__set-primary:active {
  background: var(--h5-primary-opacity);
  color: var(--h5-primary-dark);
}

.product-picker__add {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.product-picker__entry {
  display: grid;
  min-width: 0;
  min-height: 74px;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 10px;
  background: var(--h5-glass-surface);
  color: var(--h5-text-primary);
  font: inherit;
  text-align: left;
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 46%, transparent);
}
.product-picker__entry.is-active {
  border-color: color-mix(in srgb, var(--h5-primary) 58%, var(--h5-glass-border));
  background: var(--h5-primary-opacity);
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 60%, transparent), 0 4px 12px color-mix(in srgb, var(--h5-primary) 8%, transparent);
}
.product-picker__entry:disabled {
  opacity: 0.72;
}
.product-picker__entry-icon {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 20%, var(--h5-glass-border));
  border-radius: 12px;
  background: color-mix(in srgb, var(--h5-primary) 10%, var(--h5-glass-sunken));
  color: var(--h5-primary);
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 52%, transparent);
}
.product-picker__entry.is-active .product-picker__entry-icon {
  border-color: color-mix(in srgb, var(--h5-primary) 40%, transparent);
  background: color-mix(in srgb, var(--h5-primary) 16%, var(--h5-glass-sunken));
}
.product-picker__entry-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.product-picker__entry-copy strong,
.product-picker__entry-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__entry-copy strong {
  font-size: 13px;
  font-weight: 600;
}
.product-picker__entry-copy small {
  color: var(--h5-text-secondary);
  font-size: 10px;
}

.product-picker__popup {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: transparent;
}
.product-picker__popup::before {
  width: 32px;
  height: 3px;
  flex: 0 0 auto;
  align-self: center;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--h5-glass-divider);
  content: '';
}
.product-picker__sheet {
  right: 0;
  left: 0;
  width: min(100%, 10rem);
  height: min(82dvh, 720px);
  max-height: calc(100dvh - 48px);
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--h5-glass-border);
  border-bottom: 0;
  border-radius: 24px 24px 0 0;
  background: color-mix(in srgb, var(--h5-card-bg) 74%, transparent);
  box-shadow: var(--h5-glass-shadow-floating);
  backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
}
.product-picker__sheet.norem {
  border-radius: 24px 24px 0 0;
}
.product-picker__popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  min-height: 58px;
  padding: 14px 16px 10px 20px;
  background: transparent;
  color: var(--h5-text-primary);
  font-size: 18px;
  font-weight: 600;
}
.product-picker__popup-header button {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}
.product-picker__search {
  flex: 0 0 auto;
  padding: 8px 16px 12px;
  background: transparent;
}
.product-picker__search :deep(.van-search__content) {
  border: 1px solid transparent;
  background: var(--h5-glass-sunken);
}
.product-picker__popup-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: transparent;
  overscroll-behavior: contain;
}
.product-picker__popup-list :deep(.van-cell) {
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
  color: var(--h5-text-primary);
}
.product-picker__popup-list :deep(.van-cell:active) {
  background: var(--h5-primary-opacity);
}
.product-picker__popup-list :deep(.van-cell__label) {
  color: var(--h5-text-secondary);
}
.product-picker__navigation {
  display: flex;
  flex: 0 0 auto;
  min-height: 48px;
  align-items: center;
  gap: 10px;
  padding: 7px 16px;
  border-bottom: 1px solid var(--h5-divider);
  background: transparent;
}
.product-picker__back,
.product-picker__course-back {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 3px;
  padding: 7px 8px 7px 3px;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font: inherit;
  font-size: 13px;
}
.product-picker__breadcrumb {
  min-width: 0;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__sku-panel {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  background: transparent;
}
.product-picker__sku-scroll {
  flex: 1;
  min-height: 0;
  padding: 8px 16px 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.product-picker__course-back {
  margin-bottom: 6px;
}
.product-picker__course-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--h5-primary-opacity);
  border-radius: 12px;
  background: color-mix(in srgb, var(--h5-primary) 8%, var(--h5-glass-surface));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}
.product-picker__course-icon {
  display: flex;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--h5-glass-surface-strong);
  color: var(--h5-primary);
}
.product-picker__course-info {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}
.product-picker__course-info > span {
  color: var(--h5-primary);
  font-size: 10px;
  font-weight: 500;
}
.product-picker__course-info strong {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 15px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__course-info small {
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__specification,
.product-picker__sku-section {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 12px;
  background: color-mix(in srgb, var(--h5-card-bg) 62%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.68), 0 4px 14px rgba(31, 35, 48, 0.06);
}
.product-picker__section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.product-picker__section-heading strong {
  color: var(--h5-text-primary);
  font-size: 15px;
  font-weight: 600;
}
.product-picker__section-heading > span {
  color: var(--h5-text-placeholder);
  font-size: 10px;
  white-space: nowrap;
}
.product-picker__unknown-sku {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--h5-primary-light) 42%, transparent);
}
.product-picker__unknown-sku.norem {
  min-height: 44px;
  padding: 12px;
}
.product-picker__unknown-sku.norem :deep(.van-checkbox) {
  width: 100%;
  min-width: 0;
  align-items: flex-start;
}
.product-picker__unknown-sku.norem :deep(.van-checkbox__icon) {
  flex: 0 0 auto;
  margin-top: 1px;
}
.product-picker__unknown-sku.norem :deep(.van-checkbox__label) {
  min-width: 0;
  flex: 1;
  margin-left: 8px;
  color: var(--h5-text-primary);
  font-size: 14px;
  line-height: 20px;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.product-picker__attr + .product-picker__attr {
  margin-top: 18px;
}
.product-picker__attr-name {
  margin-bottom: 9px;
  color: var(--h5-text-primary);
  font-size: 13px;
  font-weight: 500;
}
.product-picker__attr-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.product-picker__attr-option {
  display: inline-flex;
  min-width: 58px;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-primary);
  font: inherit;
  font-size: 13px;
  line-height: 1.2;
}
.product-picker__attr-option.is-active {
  border-color: var(--h5-primary);
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-weight: 500;
}
.product-picker__attr-option:disabled {
  opacity: 0.35;
}
.product-picker__sku-option {
  display: flex;
  width: 100%;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 12px;
  border: 1px solid var(--h5-border);
  border-radius: 10px;
  background: var(--h5-glass-surface-strong);
  color: var(--h5-text-primary);
  text-align: left;
}
.product-picker__sku-option + .product-picker__sku-option {
  margin-top: 8px;
}
.product-picker__sku-option.is-active {
  border-color: var(--h5-primary);
  background: var(--h5-primary-opacity);
}
.product-picker__sku-info {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;
}
.product-picker__sku-info strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-picker__sku-info span {
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: normal;
  overflow-wrap: anywhere;
}
.product-picker__sku-value {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}
.product-picker__sku-value b {
  color: var(--h5-primary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.product-picker__radio {
  display: flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--h5-border);
  border-radius: 50%;
  color: #fff;
}
.product-picker__sku-option.is-active .product-picker__radio {
  border-color: var(--h5-primary);
  background: var(--h5-primary);
}
.product-picker__notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.5;
}
.product-picker__notice .van-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}
.product-picker__notice--info {
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
}
.product-picker__notice--error {
  background: rgba(255, 77, 79, 0.08);
  color: var(--h5-danger);
}
.product-picker__actions {
  flex: 0 0 auto;
  padding: 10px 16px 14px;
  border-top: 1px solid var(--h5-glass-border);
  background: color-mix(in srgb, var(--h5-card-bg) 82%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72), 0 -8px 24px rgba(31, 35, 48, 0.08);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
}
.product-picker__status {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-align: center;
}
.product-picker__status.is-complete {
  color: var(--h5-success);
}
.product-picker__status.is-error {
  color: var(--h5-danger);
}
.product-picker__actions :deep(.van-button) {
  height: 48px;
  font-size: 15px;
  font-weight: 600;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .product-picker__sheet {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .product-picker__sheet {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

</style>
