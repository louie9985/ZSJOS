<script setup lang="ts">
import { ref, computed } from 'vue'

/**
 * 省市二级 Picker
 * 数据源：后端 area-tree 接口（暂时使用 cascader columns 格式）
 */

export interface AreaNode {
  code: string
  name: string
  children?: AreaNode[]
}

const props = defineProps<{
  modelValue?: { provinceCode: string; provinceName: string; cityCode: string; cityName: string }
  areaTree: AreaNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: { provinceCode: string; provinceName: string; cityCode: string; cityName: string }]
}>()

const show = ref(false)

const displayText = computed(() => {
  if (props.modelValue?.provinceName && props.modelValue?.cityName) {
    return `${props.modelValue.provinceName} / ${props.modelValue.cityName}`
  }
  return ''
})

// 转换为 Vant Cascader 需要的格式
const cascaderOptions = computed(() => {
  return props.areaTree.map(province => ({
    text: province.name,
    value: province.code,
    children: (province.children || []).map(city => ({
      text: city.name,
      value: city.code
    }))
  }))
})

const cascaderValue = ref(props.modelValue?.cityCode || '')

function onFinish({ selectedOptions }: { selectedOptions: Array<{ text: string; value: string }> }) {
  if (selectedOptions.length >= 2) {
    emit('update:modelValue', {
      provinceCode: selectedOptions[0].value,
      provinceName: selectedOptions[0].text,
      cityCode: selectedOptions[1].value,
      cityName: selectedOptions[1].text
    })
  }
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

    <van-popup v-model:show="show" position="bottom" round>
      <van-cascader
        v-model="cascaderValue"
        title="选择地区"
        :options="cascaderOptions"
        @close="show = false"
        @finish="onFinish"
      />
    </van-popup>
  </div>
</template>
