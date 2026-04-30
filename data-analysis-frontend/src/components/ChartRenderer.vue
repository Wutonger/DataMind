<template>
  <div ref="chartRef" :style="{ height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(defineProps<{
  option?: Record<string, any>
  height?: number
}>(), {
  height: 300
})

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const palette = ['#ef5b2a', '#f08a24', '#d64550', '#f3a35c', '#b8613c', '#f7c08a']

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  if (props.option) {
    chartInstance.setOption({
      color: props.option.color || palette,
      ...props.option
    })
  }
}

watch(() => props.option, (newOption) => {
  if (chartInstance && newOption) {
    chartInstance.setOption({
      color: newOption.color || palette,
      ...newOption
    }, true)
  }
}, { deep: true })

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})

defineExpose({
  getChartInstance: () => chartInstance,
  resize: () => chartInstance?.resize()
})
</script>
