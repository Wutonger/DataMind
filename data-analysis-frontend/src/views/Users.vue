<template>
  <div class="page-shell users-page">
    <section class="surface-panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">用户权限</span>
          <h2>用户与权限</h2>
        </div>
        <n-button type="primary" @click="openCreateModal">新增用户</n-button>
      </div>

      <div class="user-toolbar">
        <div class="user-toolbar-main">
          <div class="toolbar-field toolbar-field-search">
            <n-input
              v-model:value="usernameKeyword"
              clearable
              placeholder="输入用户名进行模糊搜索"
              @keyup.enter="applyFilters"
              @clear="applyFilters"
            />
          </div>

          <div class="toolbar-field toolbar-field-status">
            <n-select
              v-model:value="statusFilter"
              clearable
              :options="statusFilterOptions"
              placeholder="全部状态"
              @update:value="handleStatusFilterChange"
            />
          </div>
        </div>

        <div class="user-toolbar-actions">
          <n-button secondary @click="applyFilters">查询</n-button>
          <n-button quaternary :disabled="!hasActiveFilters" @click="resetFilters">重置</n-button>
        </div>
      </div>

      <n-spin :show="loadingUsers">
        <div v-if="users.length" class="user-table">
          <article v-for="user in users" :key="user.id" class="user-row">
            <div class="user-main">
              <div class="user-title-row">
                <strong>{{ user.nickname || user.username }}</strong>
                <n-tag size="small" round :type="user.status === 'ACTIVE' ? 'success' : 'warning'">
                  {{ user.status === 'ACTIVE' ? '启用' : '禁用' }}
                </n-tag>
              </div>
              <span class="user-subtitle">{{ user.username }}</span>
            </div>

            <div class="user-actions">
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button
                    quaternary
                    circle
                    size="small"
                    class="action-icon-button"
                    aria-label="授权连接"
                    @click="openAccessModal(user)"
                  >
                    <template #icon>
                      <n-icon :size="16">
                        <LinkOutline />
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                授权连接
              </n-tooltip>

              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button
                    quaternary
                    circle
                    size="small"
                    class="action-icon-button"
                    :class="{ 'is-enabled': user.status !== 'ACTIVE' }"
                    :aria-label="user.status === 'ACTIVE' ? '禁用用户' : '启用用户'"
                    :loading="statusLoadingUserId === user.id"
                    @click="toggleUserStatus(user)"
                  >
                    <template #icon>
                      <n-icon :size="16">
                        <component :is="user.status === 'ACTIVE' ? BanOutline : CheckmarkCircleOutline" />
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                {{ user.status === 'ACTIVE' ? '禁用用户' : '启用用户' }}
              </n-tooltip>

              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button
                    quaternary
                    circle
                    size="small"
                    class="action-icon-button"
                    aria-label="编辑用户"
                    @click="openEditModal(user)"
                  >
                    <template #icon>
                      <n-icon :size="16">
                        <CreateOutline />
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                编辑用户
              </n-tooltip>

              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button
                    quaternary
                    circle
                    size="small"
                    class="action-icon-button is-danger"
                    aria-label="删除用户"
                    @click="removeUser(user)"
                  >
                    <template #icon>
                      <n-icon :size="16">
                        <TrashOutline />
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                删除用户
              </n-tooltip>
            </div>
          </article>
        </div>

        <div v-else class="empty-panel">
          <h3>{{ hasActiveFilters ? '没有匹配的用户' : '暂无用户' }}</h3>
          <p>
            {{ hasActiveFilters ? '请调整筛选条件后重试。' : '当前还没有可管理的普通用户。' }}
          </p>
        </div>

        <div v-if="totalUsers > 0" class="user-pagination">
          <div class="user-pagination-meta">共 {{ totalUsers }} 位用户</div>
          <n-pagination
            :page="page"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :item-count="totalUsers"
            show-size-picker
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </n-spin>
    </section>

    <n-modal
      v-model:show="showUserModal"
      preset="card"
      :title="modalTitle"
      style="width: 520px; max-width: 92vw;"
    >
      <n-form :model="formValue" label-placement="top">
        <n-form-item label="用户名">
          <n-input v-model:value="formValue.username" :disabled="isEdit" placeholder="请输入用户名" />
        </n-form-item>
        <n-form-item label="显示名称">
          <n-input v-model:value="formValue.nickname" placeholder="请输入显示名称" />
        </n-form-item>
        <n-form-item label="账号状态">
          <n-select v-model:value="formValue.status" :options="statusOptions" />
        </n-form-item>
        <n-form-item :label="isEdit ? '新密码（可选）' : '密码'">
          <n-input
            v-model:value="formValue.password"
            type="password"
            show-password-on="click"
            :placeholder="isEdit ? '留空则保持原密码' : '请输入密码'"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showUserModal = false">取消</n-button>
          <n-button type="primary" @click="submitUser">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showAccessModal"
      preset="card"
      title="授权连接"
      style="width: 560px; max-width: 92vw;"
    >
      <div class="access-shell">
        <div class="access-user-card">
          <span class="access-user-label">当前用户</span>
          <strong>{{ activeAccessUser?.username || '-' }}</strong>
        </div>

        <n-spin :show="accessLoading">
          <div class="access-field-card">
            <n-form label-placement="top">
              <n-form-item label="可访问连接">
                <n-select
                  v-model:value="accessConnectionIds"
                  multiple
                  filterable
                  clearable
                  max-tag-count="responsive"
                  :disabled="!connectionOptions.length"
                  :options="connectionOptions"
                  placeholder="选择一个或多个数据库连接"
                />
              </n-form-item>
            </n-form>

            <p v-if="!connectionOptions.length" class="access-empty-text">
              当前还没有可授权的数据库连接，请先到连接管理中创建连接。
            </p>
          </div>
        </n-spin>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showAccessModal = false">取消</n-button>
          <n-button type="primary" :loading="savingAccess" @click="submitAccessConnections">
            保存授权
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BanOutline,
  CheckmarkCircleOutline,
  CreateOutline,
  LinkOutline,
  TrashOutline
} from '@vicons/ionicons5'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NTooltip,
  useDialog,
  useMessage
} from 'naive-ui'
import axios from 'axios'

type UserItem = {
  id: number
  username: string
  nickname: string
  status: string
  lastConnectionId?: number | null
}

type ConnectionItem = {
  id: number
  name: string
  type: string
  host: string
  port: number
  database: string
  username: string
  status: string
}

type UserPageResponse = {
  items: UserItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

const message = useMessage()
const dialog = useDialog()

const users = ref<UserItem[]>([])
const connections = ref<ConnectionItem[]>([])
const loadingUsers = ref(false)
const usernameKeyword = ref('')
const showUserModal = ref(false)
const showAccessModal = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const statusLoadingUserId = ref<number | null>(null)
const activeAccessUser = ref<UserItem | null>(null)
const accessConnectionIds = ref<number[]>([])
const accessLoading = ref(false)
const savingAccess = ref(false)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref<string | null>(null)
const totalUsers = ref(0)

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
]

const statusFilterOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
]

const defaultForm = () => ({
  username: '',
  nickname: '',
  password: '',
  status: 'ACTIVE'
})

const formValue = reactive(defaultForm())

const modalTitle = computed(() => (isEdit.value ? '编辑用户' : '新增用户'))
const hasActiveFilters = computed(
  () => Boolean(usernameKeyword.value.trim()) || Boolean(statusFilter.value)
)

const connectionOptions = computed(() =>
  connections.value.map((connection) => ({
    label: connection.name,
    value: connection.id
  }))
)

const buildUserQueryParams = (targetPage: number, targetPageSize: number) => {
  const trimmedUsername = usernameKeyword.value.trim()
  return {
    page: targetPage,
    pageSize: targetPageSize,
    ...(trimmedUsername ? { username: trimmedUsername } : {}),
    ...(statusFilter.value ? { status: statusFilter.value } : {})
  }
}

const loadUsers = async (targetPage = page.value, targetPageSize = pageSize.value) => {
  loadingUsers.value = true
  try {
    const res = await axios.get<UserPageResponse>('/api/users', {
      params: buildUserQueryParams(targetPage, targetPageSize)
    })
    const payload = res.data
    users.value = Array.isArray(payload?.items) ? payload.items : []
    totalUsers.value = Number(payload?.total || 0)
    page.value = Number(payload?.page || targetPage)
    pageSize.value = Number(payload?.pageSize || targetPageSize)
  } finally {
    loadingUsers.value = false
  }
}

const handlePageChange = async (nextPage: number) => {
  await loadUsers(nextPage, pageSize.value)
}

const handlePageSizeChange = async (nextPageSize: number) => {
  await loadUsers(1, nextPageSize)
}

const applyFilters = async () => {
  await loadUsers(1, pageSize.value)
}

const handleStatusFilterChange = async (value: string | null) => {
  statusFilter.value = value
  await applyFilters()
}

const resetFilters = async () => {
  usernameKeyword.value = ''
  statusFilter.value = null
  await loadUsers(1, pageSize.value)
}

const resetUserForm = () => {
  Object.assign(formValue, defaultForm())
  editingId.value = null
}

const openCreateModal = () => {
  isEdit.value = false
  resetUserForm()
  showUserModal.value = true
}

const openEditModal = (user: UserItem) => {
  isEdit.value = true
  editingId.value = user.id
  Object.assign(formValue, {
    username: user.username,
    nickname: user.nickname,
    password: '',
    status: user.status
  })
  showUserModal.value = true
}

const openAccessModal = async (user: UserItem) => {
  activeAccessUser.value = user
  accessConnectionIds.value = []
  showAccessModal.value = true
  accessLoading.value = true
  try {
    const [connectionsRes, accessRes] = await Promise.all([
      axios.get('/api/connections'),
      axios.get(`/api/users/${user.id}/connections`)
    ])
    connections.value = Array.isArray(connectionsRes.data) ? connectionsRes.data : []
    accessConnectionIds.value = Array.isArray(accessRes.data?.connectionIds)
      ? accessRes.data.connectionIds
      : []
  } catch (error: any) {
    message.error(error?.response?.data?.message || '加载连接授权失败')
    showAccessModal.value = false
  } finally {
    accessLoading.value = false
  }
}

const submitUser = async () => {
  try {
    const editing = isEdit.value
    if (editing && editingId.value) {
      await axios.put(`/api/users/${editingId.value}`, formValue)
      message.success('用户已更新')
    } else {
      await axios.post('/api/users', formValue)
      message.success('用户已创建')
    }

    showUserModal.value = false
    if (editing) {
      await loadUsers()
    } else {
      const targetPage = Math.max(1, Math.ceil((totalUsers.value + 1) / pageSize.value))
      await loadUsers(targetPage)
    }
  } catch (error: any) {
    message.error(error?.response?.data?.message || '保存用户失败')
  }
}

const submitAccessConnections = async () => {
  if (!activeAccessUser.value) {
    return
  }

  savingAccess.value = true
  try {
    await axios.put(`/api/users/${activeAccessUser.value.id}/connections`, accessConnectionIds.value)
    message.success('连接授权已更新')
    showAccessModal.value = false
  } catch (error: any) {
    message.error(error?.response?.data?.message || '保存连接授权失败')
  } finally {
    savingAccess.value = false
  }
}

const updateUserStatus = async (user: UserItem, status: 'ACTIVE' | 'DISABLED') => {
  statusLoadingUserId.value = user.id
  try {
    await axios.put(`/api/users/${user.id}/status`, { status })
    message.success(status === 'ACTIVE' ? '用户已启用' : '用户已禁用')
    await loadUsers()
  } catch (error: any) {
    message.error(error?.response?.data?.message || '更新用户状态失败')
  } finally {
    statusLoadingUserId.value = null
  }
}

const toggleUserStatus = (user: UserItem) => {
  const nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (nextStatus === 'DISABLED') {
    dialog.warning({
      title: '禁用用户',
      content: `确认禁用“${user.nickname || user.username}”吗？禁用后该账号将无法登录。`,
      positiveText: '禁用',
      negativeText: '取消',
      onPositiveClick: () => updateUserStatus(user, nextStatus)
    })
    return
  }

  void updateUserStatus(user, nextStatus)
}

const removeUser = (user: UserItem) => {
  dialog.warning({
    title: '删除用户',
    content: `确认删除用户“${user.nickname || user.username}”吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const targetPage = users.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
      try {
        await axios.delete(`/api/users/${user.id}`)
        message.success('用户已删除')
        await loadUsers(targetPage)
      } catch (error: any) {
        message.error(error?.response?.data?.message || '删除用户失败')
      }
    }
  })
}

onMounted(() => {
  void loadUsers()
})
</script>

<style scoped>
.users-page {
  gap: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.05em;
}

.user-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.user-toolbar-main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.toolbar-field {
  min-width: 0;
}

.toolbar-field-search {
  flex: 0 0 360px;
  width: 360px;
}

.toolbar-field-status {
  flex: 0 0 180px;
  width: 180px;
}

.toolbar-field :deep(.n-input),
.toolbar-field :deep(.n-base-selection) {
  width: 100%;
}

.user-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.user-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.user-pagination-meta {
  color: var(--text-secondary);
  font-size: 13px;
}

.user-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 18px;
  background: var(--background-soft);
  border: 1px solid var(--line-soft);
}

.user-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.user-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.user-title-row strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

.user-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
}

.user-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.action-icon-button {
  background: transparent;
  box-shadow: none;
  color: var(--text-secondary);
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.action-icon-button:hover {
  color: var(--primary-color-strong);
  background: rgba(239, 91, 42, 0.1);
  transform: translateY(-1px);
}

.action-icon-button.is-enabled:hover {
  color: #1f8a63;
  background: rgba(31, 138, 99, 0.12);
}

.action-icon-button.is-danger:hover {
  color: #c85547;
  background: rgba(200, 85, 71, 0.12);
}

.access-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.access-user-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border-radius: 18px;
  background: var(--background-soft);
  border: 1px solid var(--line-soft);
}

.access-user-card strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

.access-user-label {
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.access-field-card {
  padding: 16px 18px 12px;
  border-radius: 18px;
  background: var(--surface-subtle);
  border: 1px solid var(--line-soft);
}

.access-empty-text {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 820px) {
  .user-toolbar,
  .user-row,
  .user-pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .user-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .user-toolbar-actions,
  .user-actions {
    justify-content: flex-start;
  }

  .toolbar-field-search,
  .toolbar-field-status {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
