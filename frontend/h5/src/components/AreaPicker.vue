<script setup lang="ts">
import { ref, computed } from 'vue'

/**
 * 省市二级 Picker
 * 数据源：后端 area-tree 接口（暂时使用 cascader columns 格式）
 */

export interface AreaNode {
  code: string
  name: string
  leafSelectable?: boolean
  children?: AreaNode[]
}

const OTHER_AREA_CODE = 'OTHER'

const props = defineProps<{
  modelValue?: { provinceCode: string; provinceName: string; cityCode: string; cityName: string }
  areaTree: AreaNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: { provinceCode: string; provinceName: string; cityCode: string; cityName: string }]
}>()

const show = ref(false)

const displayText = computed(() => {
  return [props.modelValue?.provinceName, props.modelValue?.cityName].filter(Boolean).join(' / ')
})

// 转换为 Vant Cascader 需要的格式
const cascaderOptions = computed(() => {
  return props.areaTree.map(province => {
    const children = province.children || []
    return {
      text: province.name,
      value: province.code,
      disabled: children.length === 0 && !province.leafSelectable,
      children: children.length
        ? children.map(city => ({
            text: city.name,
            value: city.code
          }))
        : undefined
    }
  })
})

const cascaderValue = ref(props.modelValue?.cityCode || '')

function onFinish({ selectedOptions }: { selectedOptions: Array<{ text: string; value: string }> }) {
  if (!selectedOptions.length) {
    show.value = false
    return
  }
  const province = selectedOptions[0]
  const city = selectedOptions[1]
  emit('update:modelValue', {
    provinceCode: province.value,
    provinceName: province.text,
    cityCode: city?.value || OTHER_AREA_CODE,
    cityName: city?.text || ''
  })
  show.value = false
}
</script>

<template>
  <div>
    <van-field
      :model-value="displayText"
      readonly
      clickable
      placeholder="请选择省/市"
      right-icon="arrow-down"
      @click="show = true"
    />

    <van-popup
      v-model:show="show"
      position="bottom"
      round
      teleport="body"
      class="area-picker-popup norem"
      overlay-class="submit-picker-overlay"
      safe-area-inset-bottom
    >
      <van-cascader
        v-model="cascaderValue"
        class="area-picker-cascader"
        title="选择地区"
        :options="cascaderOptions"
        @close="show = false"
        @finish="onFinish"
      />
    </van-popup>
  </div>
</template>
