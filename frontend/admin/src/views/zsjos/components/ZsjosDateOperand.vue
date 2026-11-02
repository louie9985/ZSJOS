<template>
 <div class="date-operand">
  <el-select aria-label="时间类型" :model-value="relative ? 'relative' : 'fixed'" @change="mode => emit('update:modelValue', mode === 'relative' ? initialRelativeTime() : undefined)"><el-option label="固定时间" value="fixed"/><el-option label="相对时间点" value="relative"/></el-select>
  <template v-if="relative">
   <el-select aria-label="时间基准" :model-value="relative.base" @change="base=>emit('update:modelValue',{...relative,base})"><el-option v-for="o in bases" :key="o.value" :value="o.value" :label="o.label"/></el-select>
   <el-input-number aria-label="时间偏移量" :model-value="relative.offset" :min="-10000" :max="10000" :precision="0" @update:model-value="offset=>emit('update:modelValue',{...relative,offset:offset??0})"/>
   <el-select aria-label="偏移单位" :model-value="relative.unit" @change="unit=>emit('update:modelValue',{...relative,unit})"><el-option v-for="o in units" :key="o.value" :value="o.value" :label="o.label"/></el-select>
   <small>北京时间；负数为之前，正数为之后，每次查询重新计算。</small>
  </template>
  <el-date-picker v-else :model-value="modelValue == null ? undefined : new Date(Number(modelValue))" type="datetime" value-format="x" placeholder="选择时间" @update:model-value="value=>emit('update:modelValue',value)"/>
 </div>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import {bases,units,isRelativeTime,initialRelativeTime} from './relativeTime'
const props=defineProps<{modelValue:unknown}>()
const emit=defineEmits<{'update:modelValue':[value:unknown]}>()
const relative=computed(()=>isRelativeTime(props.modelValue)?props.modelValue:undefined)
</script>
<style scoped>.date-operand{display:flex;flex-direction:column;gap:8px;min-width:0}</style>
