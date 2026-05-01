<template>
  <div class="workspace-page">
    <section class="page-section settings-page">
      <div class="section-head section-head-actions-only">
        <div class="section-actions">
          <n-button type="primary" :loading="saving" @click="saveConfig">保存配置</n-button>
        </div>
      </div>

      <div class="settings-layout">
        <div class="settings-card">
          <div class="settings-card-head">
            <span class="settings-card-kicker">接入配置</span>
          </div>

          <n-form :model="aiConfig" label-placement="top" class="settings-form-grid">
            <n-form-item label="服务商">
              <n-input v-model:value="aiConfig.provider" placeholder="openai" />
            </n-form-item>
            <n-form-item label="接口地址">
              <n-input v-model:value="aiConfig.baseUrl" placeholder="https://api.openai.com/v1" />
            </n-form-item>
            <n-form-item label="API Key" class="settings-span-2">
              <n-input
                v-model:value="aiConfig.apiKey"
                type="password"
                show-password-on="click"
                placeholder="sk-..."
              />
            </n-form-item>
          </n-form>
        </div>

        <div class="settings-model-grid">
          <div class="settings-card">
            <div class="settings-card-head">
              <span class="settings-card-kicker">语言模型</span>
            </div>

            <n-form :model="aiConfig" label-placement="top" class="settings-form-grid settings-form-single">
              <n-form-item label="模型">
                <n-input v-model:value="aiConfig.model" placeholder="例如：gpt-4o" />
              </n-form-item>
              <n-form-item label="温度">
                <div class="settings-stepper">
                  <n-button
                    class="settings-stepper-button"
                    secondary
                    circle
                    :disabled="aiConfig.temperature <= TEMPERATURE_MIN"
                    @click="adjustTemperature(-TEMPERATURE_STEP)"
                  >
                    -
                  </n-button>
                  <div class="settings-stepper-display">
                    <span class="settings-stepper-value">{{ formatTemperature(aiConfig.temperature) }}</span>
                    <span class="settings-stepper-range">
                      {{ formatTemperature(TEMPERATURE_MIN) }} - {{ formatTemperature(TEMPERATURE_MAX) }}
                    </span>
                  </div>
                  <n-button
                    class="settings-stepper-button"
                    secondary
                    circle
                    :disabled="aiConfig.temperature >= TEMPERATURE_MAX"
                    @click="adjustTemperature(TEMPERATURE_STEP)"
                  >
                    +
                  </n-button>
                </div>
              </n-form-item>
            </n-form>
          </div>

          <div class="settings-card">
            <div class="settings-card-head">
              <span class="settings-card-kicker">向量模型</span>
            </div>

            <n-form :model="aiConfig" label-placement="top" class="settings-form-grid settings-form-single">
              <n-form-item label="模型">
                <n-input
                  v-model:value="aiConfig.embeddingModel"
                  placeholder="例如：text-embedding-3-small"
                />
              </n-form-item>
            </n-form>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { configApi } from '@/api/config'

const message = useMessage()
const TEMPERATURE_MIN = 0
const TEMPERATURE_MAX = 2
const TEMPERATURE_STEP = 0.1

const aiConfig = ref({
  provider: 'openai',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  model: 'gpt-4o',
  embeddingModel: 'text-embedding-3-small',
  temperature: 0.7
})

const saving = ref(false)

const normalizeTemperature = (value: number | string) => {
  const numericValue = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(numericValue)) {
    return TEMPERATURE_MIN
  }

  const clampedValue = Math.min(TEMPERATURE_MAX, Math.max(TEMPERATURE_MIN, numericValue))
  return Math.round(clampedValue * 10) / 10
}

const formatTemperature = (value: number) => normalizeTemperature(value).toFixed(1)

const adjustTemperature = (delta: number) => {
  aiConfig.value.temperature = normalizeTemperature(aiConfig.value.temperature + delta)
}

const loadConfig = async () => {
  try {
    const res = await configApi.getAiConfig()
    aiConfig.value = {
      ...aiConfig.value,
      ...res.data,
      model: res.data?.model || aiConfig.value.model,
      embeddingModel: res.data?.embeddingModel || aiConfig.value.embeddingModel,
      temperature: normalizeTemperature(res.data?.temperature ?? aiConfig.value.temperature)
    }
  } catch (error) {
    console.error('Failed to load config', error)
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    await configApi.updateAiConfig({
      ...aiConfig.value,
      model: aiConfig.value.model?.trim() || 'gpt-4o',
      embeddingModel: aiConfig.value.embeddingModel?.trim() || 'text-embedding-3-small',
      temperature: normalizeTemperature(aiConfig.value.temperature)
    })
    message.success('配置已保存')
  } catch {
    message.error('保存配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.settings-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.settings-card {
  padding: 24px;
  border: 1px solid var(--line-soft);
  border-radius: 22px;
  background: var(--background-elevated);
}

.settings-card-head {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 18px;
}

.settings-card-kicker {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.settings-form-single {
  grid-template-columns: 1fr;
}

.settings-span-2 {
  grid-column: 1 / -1;
}

.settings-stepper {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 40px;
}

.settings-stepper-button {
  flex: 0 0 auto;
  font-size: 18px;
  font-weight: 500;
}

.settings-stepper-display {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-stepper-value {
  color: var(--text-color);
  font-size: 22px;
  line-height: 1;
  font-weight: 600;
}

.settings-stepper-range {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 960px) {
  .settings-model-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .settings-form-grid {
    grid-template-columns: 1fr;
  }

  .settings-span-2 {
    grid-column: auto;
  }
}
</style>
