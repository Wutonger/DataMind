<template>
  <div class="sql-editor-wrapper">
    <div v-if="showToolbar" class="editor-toolbar">
      <n-space size="small">
        <n-button size="tiny" @click="$emit('execute')" :loading="executing">执行</n-button>
        <n-button size="tiny" @click="$emit('format')">格式化</n-button>
        <n-button size="tiny" @click="$emit('clear')">清空</n-button>
      </n-space>
    </div>

    <textarea
      class="sql-editor-textarea"
      :value="modelValue"
      :placeholder="placeholder"
      :rows="rows"
      spellcheck="false"
      @input="handleInput"
    />
  </div>
</template>

<script setup lang="ts">
import { NButton, NSpace } from 'naive-ui'

withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    rows?: number
    executing?: boolean
    showToolbar?: boolean
  }>(),
  {
    placeholder: '',
    rows: 12,
    executing: false,
    showToolbar: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  execute: []
  format: []
  clear: []
}>()

const handleInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}
</script>

<style scoped>
.sql-editor-wrapper {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: var(--background-elevated);
}

.editor-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-soft);
  background: var(--background-muted);
}

.sql-editor-textarea {
  width: 100%;
  min-height: 280px;
  padding: 18px 20px;
  border: 0;
  outline: 0;
  resize: vertical;
  background: transparent;
  color: var(--text-color);
  caret-color: var(--primary-color);
  cursor: text;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.75;
  letter-spacing: 0.01em;
}

.sql-editor-textarea::placeholder {
  color: var(--text-muted);
}
</style>
