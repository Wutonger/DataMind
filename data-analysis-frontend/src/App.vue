<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <div class="app-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
            <aside class="sidebar-panel" :class="{ collapsed: sidebarCollapsed }">
              <div class="sidebar-topbar">
                <div class="brand-block" :class="{ collapsed: sidebarCollapsed }">
                  <div class="brand-copy">
                    <h1>{{ sidebarCollapsed ? 'DM' : 'DataMind' }}</h1>
                  </div>
                </div>

                <n-button
                  quaternary
                  circle
                  class="sidebar-toggle"
                  :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
                  @click="toggleSidebar"
                >
                  <template #icon>
                    <n-icon :size="16">
                      <component :is="sidebarCollapsed ? ChevronForwardOutline : ChevronBackOutline" />
                    </n-icon>
                  </template>
                </n-button>
              </div>

              <div v-show="!sidebarCollapsed" class="sidebar-section-label">导航</div>
              <n-menu
                v-model:value="activeKey"
                :collapsed="sidebarCollapsed"
                :collapsed-width="64"
                :collapsed-icon-size="18"
                :options="menuOptions"
                @update:value="handleMenuSelect"
              />
            </aside>

            <main class="workspace-shell">
              <div class="workspace-body">
                <router-view v-slot="{ Component, route }">
                  <keep-alive>
                    <component v-if="route.meta.keepAlive" :is="Component" />
                  </keep-alive>
                  <component v-if="!route.meta.keepAlive" :is="Component" />
                </router-view>
              </div>
            </main>
          </div>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AnalyticsOutline,
  BarChartOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  ChatbubbleEllipsesOutline,
  GitNetworkOutline,
  LinkOutline,
  LibraryOutline,
  SettingsOutline,
  ShareSocialOutline,
  TerminalOutline
} from '@vicons/ionicons5'
import {
  NConfigProvider,
  NDialogProvider,
  NIcon,
  NMenu,
  NMessageProvider,
  NNotificationProvider
} from 'naive-ui'
import type { Component } from 'vue'
import type { GlobalThemeOverrides, MenuOption } from 'naive-ui'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

type MenuMeta = {
  key: string
  label: string
  icon: Component
}

const menuItems: MenuMeta[] = [
  { key: 'Dashboard', label: '首页', icon: AnalyticsOutline },
  { key: 'Connections', label: '连接管理', icon: LinkOutline },
  { key: 'Chat', label: '智能执行', icon: ChatbubbleEllipsesOutline },
  { key: 'SqlStudio', label: 'SQL 工作台', icon: TerminalOutline },
  { key: 'Reports', label: '报表中心', icon: BarChartOutline },
  { key: 'Analysis', label: '表结构', icon: GitNetworkOutline },
  { key: 'Knowledge', label: '知识库', icon: LibraryOutline },
  { key: 'Workflow', label: '执行链路', icon: ShareSocialOutline },
  { key: 'Settings', label: '系统设置', icon: SettingsOutline }
]

const renderIcon = (icon: Component) => () => h(NIcon, { size: 18 }, { default: () => h(icon) })

const savedKey = localStorage.getItem('activeMenuKey') || String(route.name || 'Dashboard')
const activeKey = ref(savedKey)
const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

const menuOptions: MenuOption[] = menuItems.map((item) => ({
  key: item.key,
  label: item.label,
  icon: renderIcon(item.icon)
}))

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#ef5b2a',
    primaryColorHover: '#e25221',
    primaryColorPressed: '#cb4416',
    primaryColorSuppl: '#ef5b2a',
    infoColor: '#d64550',
    successColor: '#1f8a63',
    warningColor: '#f08a24',
    errorColor: '#c85547',
    borderRadius: '14px',
    fontFamily: 'var(--font-body)',
    fontFamilyMono: 'var(--font-mono)'
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusSmall: '12px',
    heightMedium: '40px',
    heightSmall: '34px',
    textColorPrimary: '#fff8f4',
    colorPrimary: '#ef5b2a',
    colorPrimaryHover: '#e25221',
    colorPrimaryPressed: '#cb4416'
  },
  Menu: {
    color: 'transparent',
    itemColorHover: 'rgba(239, 91, 42, 0.05)',
    itemColorActive: 'rgba(239, 91, 42, 0.09)',
    itemColorActiveHover: 'rgba(239, 91, 42, 0.13)',
    itemTextColor: '#6e5548',
    itemTextColorHover: '#3f3129',
    itemTextColorActive: '#b4542d',
    itemTextColorActiveHover: '#b4542d',
    itemTextColorChildActive: '#b4542d',
    itemTextColorChildActiveHover: '#b4542d',
    itemIconColor: '#b47a62',
    itemIconColorHover: '#b4542d',
    itemIconColorActive: '#b4542d',
    itemIconColorActiveHover: '#b4542d',
    itemIconColorChildActive: '#b4542d',
    itemIconColorChildActiveHover: '#b4542d'
  },
  Tabs: {
    colorSegment: '#fff7f2',
    tabColorSegment: 'rgba(239, 91, 42, 0.12)',
    tabTextColorSegment: '#8f6b5a',
    tabTextColorHoverSegment: '#b4542d',
    tabTextColorActiveSegment: '#b4542d',
    barColor: '#ef5b2a',
    tabTextColorActiveLine: '#ef5b2a',
    tabTextColorHoverLine: '#ef5b2a',
    tabTextColorActiveBar: '#ef5b2a',
    tabTextColorHoverBar: '#ef5b2a',
    tabTextColorActiveCard: '#ef5b2a',
    tabTextColorHoverCard: '#ef5b2a'
  },
  Card: {
    borderRadius: '18px'
  },
  Input: {
    borderRadius: '14px'
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '14px'
      }
    }
  },
  Modal: {
    borderRadius: '18px'
  }
}

watch(activeKey, (newVal) => {
  localStorage.setItem('activeMenuKey', newVal)
})

watch(sidebarCollapsed, (value) => {
  localStorage.setItem('sidebarCollapsed', String(value))
})

watch(
  () => route.name,
  (newVal) => {
    if (newVal) {
      activeKey.value = String(newVal)
    }
  }
)

onMounted(() => {
  appStore.loadActiveConnection()
})

const handleMenuSelect = (key: string) => {
  router.push({ name: key })
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: start;
  height: 100vh;
  min-height: 100vh;
  gap: 16px;
  padding: 16px;
  transition: grid-template-columns 0.22s ease;
}

.app-shell.sidebar-collapsed {
  grid-template-columns: 92px minmax(0, 1fr);
}

.sidebar-panel {
  position: sticky;
  top: 16px;
  align-self: start;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 32px);
  max-height: calc(100vh - 32px);
  padding: 22px 16px 16px;
  border-radius: 24px;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
  box-shadow: var(--card-shadow);
  overflow: auto;
  scrollbar-gutter: stable;
  transition:
    padding 0.22s ease,
    border-radius 0.22s ease;
}

.sidebar-panel.collapsed {
  padding-left: 12px;
  padding-right: 12px;
}

.sidebar-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 24px;
}

.brand-block {
  display: flex;
  justify-content: flex-start;
  gap: 0;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.brand-block.collapsed {
  justify-content: center;
}

.brand-copy {
  width: 100%;
  min-width: 0;
  text-align: left;
}

.brand-block.collapsed .brand-copy {
  text-align: center;
}

.brand-copy h1 {
  margin: 0;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.05em;
}

.brand-block.collapsed .brand-copy h1 {
  font-size: 22px;
  letter-spacing: -0.04em;
}

.sidebar-toggle {
  flex: 0 0 auto;
  color: var(--text-secondary);
}

.sidebar-toggle:hover {
  color: var(--primary-color-strong);
}

.sidebar-section-label {
  margin: 6px 0 10px;
  padding-left: 10px;
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.workspace-shell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: calc(100vh - 32px);
}

.workspace-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px;
}

.sidebar-panel :deep(.n-menu) {
  background: transparent !important;
}

.sidebar-panel.collapsed :deep(.n-menu) {
  width: 100%;
}

.sidebar-panel :deep(.n-menu-item-content) {
  position: relative;
  margin: 4px 0;
  min-height: 46px;
  border-radius: 14px !important;
  overflow: hidden;
  transition: background 0.18s ease;
}

.sidebar-panel :deep(.n-menu-item-content::before) {
  border-radius: 14px !important;
  background: transparent !important;
  box-shadow: none !important;
}

.sidebar-panel :deep(.n-menu-item-content-header) {
  color: #6e5548;
  font-size: 14px;
  font-weight: 600;
}

.sidebar-panel :deep(.n-menu-item-content__icon) {
  color: #b47a62;
}

.sidebar-panel.collapsed :deep(.n-menu-item-content) {
  justify-content: center;
  margin: 6px 0;
}

.sidebar-panel :deep(.n-menu-item-content:hover) {
  background: var(--surface-hover);
}

.sidebar-panel :deep(.n-menu-item-content:hover .n-menu-item-content-header),
.sidebar-panel :deep(.n-menu-item-content:hover .n-menu-item-content__icon) {
  color: var(--primary-color-strong);
}

.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected) {
  background: var(--surface-active);
}

.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected:hover) {
  background: var(--surface-active-strong);
}

.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected .n-menu-item-content-header),
.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected .n-menu-item-content__icon) {
  color: var(--primary-color-strong);
}

@media (max-width: 920px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .app-shell.sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .sidebar-panel {
    position: relative;
    top: 0;
    align-self: stretch;
    height: auto;
    max-height: none;
    min-height: auto;
    overflow: visible;
  }

  .workspace-shell {
    min-height: auto;
    height: auto;
  }

  .sidebar-panel.collapsed {
    padding-left: 16px;
    padding-right: 16px;
  }
}

@media (max-width: 680px) {
  .app-shell {
    padding: 12px;
    gap: 12px;
  }
}
</style>
