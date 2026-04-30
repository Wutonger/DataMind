<template>
  <div class="data-table-wrapper">
    <div v-if="columns.length > 0" class="result-table-wrapper">
      <table class="result-table">
        <thead>
          <tr>
            <th v-for="col in columns" :key="col">{{ col }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
            <td v-for="col in columns" :key="col">{{ formatCell(row[col]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="error" class="result-error">
      <p>{{ error }}</p>
    </div>

    <div v-else class="result-empty">
      <p>{{ emptyText }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    columns: string[]
    rows: any[]
    error?: string
    emptyText?: string
  }>(),
  {
    emptyText: '暂无数据'
  }
)

const formatCell = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return '—'
  }

  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }

  return String(value)
}
</script>

<style scoped>
.data-table-wrapper {
  min-height: 0;
}

.result-table-wrapper {
  overflow: auto;
  max-height: 500px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
}

.result-table {
  width: 100%;
  min-width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.result-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 13px 16px;
  background: rgba(250, 250, 250, 0.96);
  text-align: left;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
  border-bottom: 1px solid var(--line-soft);
  white-space: nowrap;
}

.result-table td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-color);
  white-space: nowrap;
}

.result-table tr:nth-child(even) td {
  background: rgba(250, 250, 250, 0.55);
}

.result-table tr:hover td {
  background: rgba(115, 77, 57, 0.04);
}

.result-error {
  padding: 20px;
  background: rgba(200, 85, 71, 0.08);
  border: 1px solid rgba(200, 85, 71, 0.16);
  border-radius: 18px;
  color: var(--error-color);
}

.result-empty {
  padding: 42px 20px;
  text-align: center;
  color: var(--text-secondary);
  border-radius: 18px;
  background: rgba(255, 248, 246, 0.92);
}
</style>
