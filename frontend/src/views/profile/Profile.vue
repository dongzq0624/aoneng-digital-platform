<template>
  <div class="profile-page" v-loading="loading">
    <div class="page-head compact">
      <div>
        <p class="eyebrow">账号设置</p>
        <h1>个人中心</h1>
        <p>查看当前登录账号的基本信息与组织归属。</p>
      </div>
      <el-button :icon="Refresh" plain :loading="loading" @click="loadProfile">刷新信息</el-button>
    </div>

    <section v-if="user" class="profile-grid" aria-label="个人信息">
      <article class="profile-summary">
        <div class="profile-avatar" aria-hidden="true">{{ displayName.slice(0, 1) }}</div>
        <div>
          <h2>{{ displayName }}</h2>
          <p>{{ user.username }}</p>
        </div>
        <el-tag type="success" effect="light">当前会话已验证</el-tag>
      </article>

      <article class="profile-details">
        <div class="profile-card-head">
          <div>
            <h2>账号信息</h2>
            <p>信息由当前登录账号同步提供。</p>
          </div>
        </div>
        <dl>
          <div>
            <dt>姓名</dt>
            <dd>{{ displayName }}</dd>
          </div>
          <div>
            <dt>登录账号</dt>
            <dd>{{ user.username }}</dd>
          </div>
          <div>
            <dt>所属部门</dt>
            <dd>{{ departmentName }}</dd>
          </div>
          <div>
            <dt>账号编号</dt>
            <dd>{{ user.id }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <el-result
      v-else-if="!loading"
      icon="warning"
      title="个人信息加载失败"
      sub-title="请检查网络连接后重试。"
    >
      <template #extra>
        <el-button type="primary" :icon="Refresh" @click="loadProfile">重新加载</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Refresh} from '@element-plus/icons-vue'
import {authApi, systemApi, type Department, type UserInfo} from '../../api'

const loading = ref(false)
const user = ref<UserInfo>()
const departments = ref<Department[]>([])

const displayName = computed(() => user.value?.realName || user.value?.username || '员工')
const departmentName = computed(() => {
  const dept = departments.value.find(item => item.id === user.value?.deptId)
  return dept?.name || '未分配部门'
})

async function loadProfile() {
  loading.value = true
  try {
    const {data: currentUser} = await authApi.me()
    user.value = currentUser
    try {
      const {data: deptRows} = await systemApi.departments()
      departments.value = Array.isArray(deptRows)
        ? deptRows.map((item: Department & { parentid?: number }) => ({...item, parentId: item.parentId ?? item.parentid ?? 0}))
        : []
    } catch {
      departments.value = []
    }
  } catch (error) {
    user.value = undefined
    ElMessage.error(error instanceof Error ? error.message : '个人信息加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { width: 100%; max-width: none; }

.profile-grid {
  display: grid;
  grid-template-columns: minmax(260px, .7fr) minmax(0, 1.3fr);
  gap: 16px;
}

.profile-summary,
.profile-details {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  box-shadow: var(--shadow-panel);
}

.profile-summary {
  min-height: 220px;
  padding: 28px 24px;
  display: flex;
  align-items: center;
  align-content: center;
  flex-wrap: wrap;
  gap: 14px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  flex: 0 0 56px;
  border-radius: 50%;
  color: var(--teal-deep);
  background: var(--soft-green);
  font-size: 22px;
  font-weight: 700;
}

h2 { margin: 0; color: var(--ink); font-size: 18px; }
.profile-summary p, .profile-card-head p { margin: 5px 0 0; color: var(--text-muted); font-size: 13px; }
.profile-summary .el-tag { width: 100%; margin-top: 10px; }

.profile-details { padding: 24px; }
.profile-card-head { margin-bottom: 20px; }

dl { margin: 0; }
dl > div {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 16px;
  min-height: 52px;
  padding: 15px 0;
  border-top: 1px solid var(--border-subtle);
}
dt { color: var(--text-muted); font-size: 13px; }
dd { min-width: 0; margin: 0; color: var(--text-strong); font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }

@media (max-width: 760px) {
  .profile-grid { grid-template-columns: 1fr; }
  .profile-summary { min-height: 0; }
  dl > div { grid-template-columns: 96px minmax(0, 1fr); gap: 12px; }
}
</style>
