<template>
  <n-tag :type="tagType" size="small" :bordered="false">
    {{ label }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps<{
  status: 'active' | 'inactive' | 'success' | 'error' | 'warning' | 'loading'
}>()

const statusMap: Record<
  string,
  { type: 'success' | 'default' | 'error' | 'warning' | 'info'; label: string }
> = {
  active: { type: 'success', label: '已激活' },
  inactive: { type: 'default', label: '未激活' },
  success: { type: 'success', label: '成功' },
  error: { type: 'error', label: '失败' },
  warning: { type: 'warning', label: '警告' },
  loading: { type: 'info', label: '加载中' }
}

const tagType = computed(() => statusMap[props.status]?.type || 'default')
const label = computed(() => statusMap[props.status]?.label || props.status)
</script>
