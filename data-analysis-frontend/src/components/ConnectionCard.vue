<template>
  <div class="card connection-card" :class="{ selected: selected }" @click="$emit('select', connection)">
    <span v-if="selected" class="active-badge">当前连接</span>

    <div class="card-header">
      <h3>{{ connection.name }}</h3>
      <StatusBadge :status="status" />
    </div>

    <div class="connection-info">
      <p><strong>类型：</strong><span>{{ connection.type }}</span></p>
      <p><strong>地址：</strong><span>{{ connection.host }}:{{ connection.port }}</span></p>
      <p><strong>数据库：</strong><span>{{ connection.database }}</span></p>
    </div>

    <n-space style="margin-top: 16px;">
      <n-button size="small" @click.stop="$emit('test', connection)">测试</n-button>
      <n-button size="small" @click.stop="$emit('edit', connection)">编辑</n-button>
      <n-button size="small" type="error" @click.stop="$emit('delete', connection)">删除</n-button>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NSpace } from 'naive-ui'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{
  connection: any
  selected?: boolean
}>()

defineEmits<{
  select: [connection: any]
  test: [connection: any]
  edit: [connection: any]
  delete: [connection: any]
}>()

const status = computed(() => (props.selected ? 'active' : 'inactive'))
</script>

<style scoped>
.connection-card {
  cursor: pointer;
}

.active-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--primary-color-soft);
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.connection-info {
  display: grid;
  gap: 8px;
  color: var(--text-secondary);
}

.connection-info p {
  margin: 0;
}

.connection-info strong {
  color: var(--text-color);
}
</style>
