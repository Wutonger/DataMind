<template>
  <div class="sql-editor-wrapper">
    <div v-if="showToolbar" class="editor-toolbar">
      <n-space size="small">
        <n-button size="tiny" @click="$emit('execute')" :loading="executing">执行</n-button>
        <n-button size="tiny" @click="$emit('format')">格式化</n-button>
        <n-button size="tiny" @click="$emit('clear')">清空</n-button>
      </n-space>
    </div>

    <n-input
      :value="modelValue"
      @update:value="$emit('update:modelValue', $event)"
      type="textarea"
      :placeholder="placeholder"
      :rows="rows"
      class="sql-editor"
    />
  </div>
</template>

<script setup lang="ts">
import { NButton, NInput, NSpace } from 'naive-ui'

defineProps<{
  modelValue: string
  placeholder?: string
  rows?: number
  executing?: boolean
  showToolbar?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
  execute: []
  format: []
  clear: []
}>()
</script>

<style scoped>
.sql-editor-wrapper {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.98);
}

.editor-toolbar {
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-soft);
  background: rgba(250, 246, 243, 0.92);
  display: flex;
  justify-content: flex-end;
}

.sql-editor-wrapper :deep(.n-input) {
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: none !important;
  --n-color: transparent !important;
  --n-color-focus: transparent !important;
  --n-text-color: var(--text-color) !important;
  --n-placeholder-color: rgba(149, 113, 91, 0.5) !important;
}

.sql-editor-wrapper :deep(.n-input__textarea-el),
.sql-editor-wrapper :deep(.n-input__textarea-mirror),
.sql-editor-wrapper :deep(.n-input__placeholder) {
  box-sizing: border-box;
  padding: 18px 20px !important;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.75 !important;
  letter-spacing: 0.01em;
}

.sql-editor-wrapper :deep(.n-input__textarea-el) {
  min-height: 280px;
}
</style>
