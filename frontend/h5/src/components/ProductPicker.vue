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

// 搜索结果
const filteredSpus = computed(() => {
  if (!searchText.value.trim()) return props.spus
  const keyword = searchText.value.trim().toLowerCase()
  return props.spus.filter(spu =>
    spu.spuName.toLowerCase().includes(keyword) ||
    spu.categoryName?.toLowerCase().includes(keyword)
  )
})

// 已选的 spuRef 集合
const selectedRefs = computed(() => new Set(props.modelValue.map(p => p.spuRef)))

function selectSpu(spu: SpuItem) {
  if (selectedRefs.value.has(spu.spuRef)) return // 不重复添加

  const newItem: SelectedProduct = {
    spuRef: spu.spuRef,
    spuName: spu.spuName,
    skuRef: undefined,
    skuName: undefined,
    spuUnknown: false,
    skuUnknown: true,
    primary: props.modelValue.length === 0 // 第一个默认为主意向
  }
  emit('update:modelValue', [...props.modelValue, newItem])
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
        <div class="product-picker__popup-list">
          <van-cell
            v-for="spu in filteredSpus"
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
          <van-empty v-if="filteredSpus.length === 0" description="无匹配课程" image="search" />
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
</style>
