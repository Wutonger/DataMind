<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <router-view v-if="route.meta.hideShell" />

          <div v-else class="app-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
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
              <header class="workspace-topbar">
                <div class="workspace-meta">
                  <span class="workspace-label">当前连接</span>
                  <div class="workspace-connection">
                    <n-select
                      v-model:value="selectedConnectionId"
                      :options="connectionOptions"
                      :loading="appStore.loadingConnections"
                      clearable
                      filterable
                      placeholder="请选择连接"
                      size="medium"
                      @update:value="handleConnectionChange"
                    />
                  </div>
                </div>

                <div class="workspace-user">
                  <div class="user-chip">
                    <span class="user-name">{{ authStore.displayName }}</span>
                    <span class="user-role">{{ authStore.isAdmin ? '管理员' : '成员' }}</span>
                  </div>
                  <n-tooltip trigger="hover" placement="bottom-end">
                    <template #trigger>
                      <n-button
                        quaternary
                        circle
                        size="small"
                        class="logout-button"
                        aria-label="退出登录"
                        @click="handleLogout"
                      >
                        <template #icon>
                          <n-icon :size="16">
                            <LogOutOutline />
                          </n-icon>
                        </template>
                      </n-button>
                    </template>
                    退出登录
                  </n-tooltip>
                </div>
              </header>

              <div class="workspace-body">
                <router-view v-slot="{ Component, route: currentRoute }">
                  <keep-alive>
                    <component v-if="currentRoute.meta.keepAlive" :is="Component" />
                  </keep-alive>
                  <component v-if="!currentRoute.meta.keepAlive" :is="Component" />
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
import { computed, h, onMounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AnalyticsOutline,
  BarChartOutline,
  ChatbubbleEllipsesOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  GitNetworkOutline,
  LibraryOutline,
  LinkOutline,
  LogOutOutline,
  PeopleOutline,
  SettingsOutline,
  ShareSocialOutline,
  TerminalOutline
} from '@vicons/ionicons5'
import {
  NButton,
  NConfigProvider,
  NDialogProvider,
  NIcon,
  NMenu,
  NMessageProvider,
  NNotificationProvider,
  NSelect,
  NTooltip
} from 'naive-ui'
import type { GlobalThemeOverrides, MenuOption } from 'naive-ui'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

type MenuMeta = {
  key: string
  label: string
  icon: Component
  adminOnly?: boolean
}

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()

const menuItems: MenuMeta[] = [
  { key: 'Dashboard', label: '首页', icon: AnalyticsOutline },
  { key: 'Connections', label: '连接管理', icon: LinkOutline },
  { key: 'Chat', label: '智能执行', icon: ChatbubbleEllipsesOutline },
  { key: 'SqlStudio', label: 'SQL 工作台', icon: TerminalOutline },
  { key: 'Analysis', label: '表结构', icon: GitNetworkOutline },
  { key: 'Reports', label: '报表中心', icon: BarChartOutline },
  { key: 'Workflow', label: '执行链路', icon: ShareSocialOutline },
  { key: 'Knowledge', label: '知识库', icon: LibraryOutline, adminOnly: true },
  { key: 'Settings', label: '系统设置', icon: SettingsOutline, adminOnly: true },
  { key: 'Users', label: '用户与权限', icon: PeopleOutline, adminOnly: true }
]

const renderIcon = (icon: Component) => () => h(NIcon, { size: 18 }, { default: () => h(icon) })

const activeKey = ref(localStorage.getItem('activeMenuKey') || String(route.name || 'Dashboard'))
const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')
const menuDisplayOrder = [
  'Dashboard',
  'Connections',
  'Chat',
  'SqlStudio',
  'Analysis',
  'Reports',
  'Workflow',
  'Knowledge',
  'Users',
  'Settings'
]

const menuOptions = computed<MenuOption[]>(() =>
  menuItems
    .filter((item) => !item.adminOnly || authStore.isAdmin)
    .slice()
    .sort(
      (left, right) =>
        menuDisplayOrder.indexOf(left.key) - menuDisplayOrder.indexOf(right.key)
    )
    .map((item) => ({
      key: item.key,
      label: item.label,
      icon: renderIcon(item.icon)
    }))
)

const connectionOptions = computed(() =>
  appStore.accessibleConnections.map((item) => ({
    label: item.name,
    value: item.id
  }))
)

const selectedConnectionId = computed({
  get: () => appStore.currentConnectionId,
  set: (value) => appStore.setCurrentConnection(value)
})

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
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '14px'
      }
    }
  }
}

const bootstrapWorkspace = async () => {
  if (!authStore.isLoggedIn) {
    appStore.reset()
    return
  }

  try {
    await authStore.ensureSession()
    await appStore.loadAccessibleConnections(authStore.user?.lastConnectionId ?? null)
  } catch (error) {
    console.error('Failed to bootstrap workspace', error)
    appStore.reset()
  }
}

watch(activeKey, (value) => {
  localStorage.setItem('activeMenuKey', value)
})

watch(sidebarCollapsed, (value) => {
  localStorage.setItem('sidebarCollapsed', String(value))
})

watch(
  () => route.name,
  (value) => {
    if (value) {
      activeKey.value = String(value)
    }
  }
)

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      bootstrapWorkspace()
      return
    }
    appStore.reset()
  }
)

onMounted(() => {
  bootstrapWorkspace()
})

const handleMenuSelect = (key: string) => {
  if (route.name !== key) {
    router.push({ name: key })
  }
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleConnectionChange = async (value: number | null) => {
  await appStore.selectConnection(value)
}

const handleLogout = async () => {
  await authStore.logout()
  appStore.reset()
  await router.replace({ name: 'Login' })
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
  flex: 1;
  align-items: center;
  justify-content: flex-start;
  min-width: 0;
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
}

.sidebar-toggle {
  color: var(--text-secondary);
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
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  height: calc(100vh - 32px);
}

.workspace-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-radius: 20px;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
  box-shadow: var(--card-shadow);
}

.workspace-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.workspace-label {
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.workspace-connection {
  min-width: 280px;
}

.workspace-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--background-soft);
  border: 1px solid var(--line-soft);
}

.user-name {
  color: var(--text-color);
  font-weight: 600;
  font-size: 14px;
  line-height: 1;
}

.user-role {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.logout-button {
  width: 36px;
  height: 36px;
  color: var(--text-secondary);
}

.logout-button:hover {
  color: var(--primary-color-strong);
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

.sidebar-panel :deep(.n-menu-item-content) {
  position: relative;
  margin: 4px 0;
  min-height: 46px;
  border-radius: 14px !important;
  overflow: hidden;
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

.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected .n-menu-item-content-header),
.sidebar-panel :deep(.n-menu-item-content.n-menu-item-content--selected .n-menu-item-content__icon) {
  color: var(--primary-color-strong);
}

@media (max-width: 920px) {
  .app-shell,
  .app-shell.sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .sidebar-panel {
    position: relative;
    top: 0;
    height: auto;
    max-height: none;
  }

  .workspace-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-connection {
    min-width: 0;
  }

  .workspace-user {
    justify-content: space-between;
  }
}
</style>
