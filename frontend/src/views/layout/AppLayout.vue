<template>
  <div class="layout" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <a class="skip-link" href="#main-content">跳至正文</a>
    <aside class="side">
      <div class="brand"><span>奥</span>
        <div><b>奥能电源</b><small>员工数字化平台</small></div>
      </div>
      <p class="menu-label">我的工作台</p>
      <nav aria-label="主导航">
        <template v-for="item in visibleMenus" :key="item.key">
          <el-tooltip v-if="!item.children" :content="item.label" placement="right" :disabled="!sidebarCollapsed">
            <RouterLink :to="item.path || '/dashboard'" class="menu-item" :aria-label="item.label">
              <span class="menu-icon"><el-icon><component :is="item.icon" /></el-icon></span>
              <span class="menu-item-label">{{ item.label }}</span>
              <em v-if="item.badge">{{ item.badge }}</em>
            </RouterLink>
          </el-tooltip>
          <div v-else class="menu-group" :class="{ 'is-active': isSystemRoute, 'is-expanded': systemMenuExpanded }" @mouseenter="openSystemFlyout()" @mouseleave="scheduleSystemFlyoutClose">
            <el-tooltip :content="item.label" placement="right" :disabled="!sidebarCollapsed">
              <button ref="systemMenuTrigger" class="menu-item menu-group-trigger" :class="{ 'is-flyout-open': sidebarCollapsed && !isMobileNav && systemFlyoutOpen }" type="button" :aria-label="item.label" :aria-expanded="sidebarCollapsed && !isMobileNav ? systemFlyoutOpen : systemMenuExpanded" aria-haspopup="true" @click="toggleSystemMenu" @keydown.esc.stop="closeSystemFlyout">
                <span class="menu-icon"><el-icon><component :is="item.icon" /></el-icon></span>
                <span class="menu-item-label">{{ item.label }}</span>
                <el-icon class="menu-group-arrow" aria-hidden="true"><ArrowDown /></el-icon>
              </button>
            </el-tooltip>
            <div v-show="systemMenuExpanded && (!sidebarCollapsed || isMobileNav)" class="menu-children">
              <RouterLink v-for="child in item.children" :key="child.path" :to="child.path || '/dashboard'" class="menu-item menu-child" :aria-label="child.label">
                <span class="menu-icon"><el-icon><component :is="child.icon" /></el-icon></span>
                <span class="menu-item-label">{{ child.label }}</span>
              </RouterLink>
            </div>
            <Teleport to="body">
              <Transition name="sidebar-flyout">
                <aside v-if="sidebarCollapsed && !isMobileNav && systemFlyoutOpen" ref="systemFlyout" class="sidebar-flyout" :style="systemFlyoutStyle" aria-label="系统管理菜单" @mouseenter="cancelSystemFlyoutClose" @mouseleave="scheduleSystemFlyoutClose" @keydown.esc.stop="closeSystemFlyout">
                  <div class="sidebar-flyout-title">{{ item.label }}</div>
                  <RouterLink v-for="child in item.children" :key="child.path" :to="child.path || '/dashboard'" class="sidebar-flyout-item" :class="{ 'is-current': route.path === child.path }" :aria-current="route.path === child.path ? 'page' : undefined" @click="closeSystemFlyout">
                    <el-icon aria-hidden="true"><component :is="child.icon" /></el-icon>
                    <span>{{ child.label }}</span>
                  </RouterLink>
                </aside>
              </Transition>
            </Teleport>
          </div>
        </template>
      </nav>
      <div class="side-footer">
        <el-tooltip :content="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" placement="right">
          <button class="sidebar-toggle" type="button" :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed">
            <el-icon aria-hidden="true"><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
            <span>{{ sidebarCollapsed ? '展开菜单' : '收起菜单' }}</span>
          </button>
        </el-tooltip>
      </div>
    </aside>
    <main id="main-content" class="main" tabindex="-1">
      <header class="top">
        <div class="crumb"><el-icon aria-hidden="true"><HomeFilled /></el-icon><span>奥能电源</span><i>/</i><b>{{ title }}</b></div>
        <div class="top-right">
          <el-input v-model="search" placeholder="搜索知识库、文档或同事" class="search" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-tooltip :content="isDark ? '切换为浅色模式' : '切换为暗夜模式'" placement="bottom">
            <button class="theme-toggle" type="button" :aria-label="isDark ? '切换为浅色模式' : '切换为暗夜模式'" :aria-pressed="isDark" @click="isDark = !isDark">
              <el-icon aria-hidden="true"><component :is="isDark ? Sunny : Moon" /></el-icon>
            </button>
          </el-tooltip>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user user-menu" type="button" aria-label="打开用户菜单">
              <span>{{ userName.slice(0, 1) }}</span>
              <div><b>{{ userName }}</b><small>{{ deptName }}</small></div>
              <el-icon class="user-menu-arrow" aria-hidden="true"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" class="logout-menu-item">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <nav class="workspace-tabs" aria-label="已打开的菜单">
        <div v-for="tab in openTabs" :key="tab.path" class="workspace-tab" :class="{active: route.path === tab.path}">
          <RouterLink :to="tab.path" class="workspace-tab-link" :aria-current="route.path === tab.path ? 'page' : undefined">
            <el-icon aria-hidden="true"><component :is="iconForPath(tab.path)" /></el-icon>
            <span>{{ tab.title }}</span>
          </RouterLink>
          <button v-if="tab.closable" class="workspace-tab-close" type="button" :aria-label="`关闭${tab.title}`" @click="closeTab(tab.path)">
            <el-icon aria-hidden="true"><Close /></el-icon>
          </button>
        </div>
      </nav>
      <section class="page page-fluid" :class="{'is-chat-page': route.path === '/chat'}">
        <RouterView/>
      </section>
    </main>
  </div>
</template>
<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {ArrowDown, ChatDotRound, Close, DataAnalysis, DocumentChecked, Expand, Fold, FolderOpened, HomeFilled, Menu, Moon, OfficeBuilding, Search, Setting, Sunny, SwitchButton, User, UserFilled, Timer} from '@element-plus/icons-vue';
import {useRoute, useRouter} from 'vue-router'
import {authApi, systemApi} from '../../api'

const route = useRoute();
const router = useRouter();
const search = ref('');
const THEME_STORAGE_KEY = 'rag_theme';
const SIDEBAR_STORAGE_KEY = 'rag_sidebar_collapsed';
const readStoredBoolean = (key: string) => {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
};
const isDark = ref(readStoredBoolean(THEME_STORAGE_KEY));
const sidebarCollapsed = ref(readStoredBoolean(SIDEBAR_STORAGE_KEY));
const systemMenuExpanded = ref(false);
const isMobileNav = ref(false);
const systemFlyoutOpen = ref(false);
const systemMenuTrigger = ref<HTMLButtonElement | HTMLButtonElement[]>();
const systemFlyout = ref<HTMLElement | HTMLElement[]>();
const systemFlyoutPosition = ref({top: 12, left: 76});
let systemFlyoutCloseTimer: ReturnType<typeof setTimeout> | undefined;
const userName = ref('员工');
const deptName = ref('');
const allowedMenuIds = ref<number[] | null>(null);
interface WorkspaceTab { path: string; title: string; closable: boolean }
interface NavigationItem { key: string; id?: number; path?: string; label: string; icon: unknown; badge?: string; children?: NavigationItem[] }
const TAB_STORAGE_KEY = 'rag_workspace_tabs';
const menus: NavigationItem[] = [{key: 'dashboard', id: 1, path: '/dashboard', label: '概览', icon: HomeFilled}, {
  key: 'knowledge-bases', id: 2,
  path: '/kb',
  label: '知识库管理',
  icon: FolderOpened
}, {key: 'chat', id: 3, path: '/chat', label: '智能问答', icon: ChatDotRound, badge: '智能'}, {
  key: 'system-management',
  label: '系统管理',
  icon: Setting,
  children: [
    {key: 'users', id: 5, path: '/system/users', label: '用户管理', icon: UserFilled},
    {key: 'departments', id: 6, path: '/system/depts', label: '部门管理', icon: OfficeBuilding},
    {key: 'roles', id: 7, path: '/system/roles', label: '角色管理', icon: User},
    {key: 'menus', id: 8, path: '/system/menus', label: '菜单管理', icon: Menu},
  ],
}, {key: 'audit', id: 9, path: '/audit', label: '审计日志', icon: DocumentChecked}, {key: 'monitoring', id: 10, path: '/monitoring', label: 'RAGAS 评估', icon: DataAnalysis}, {key: 'file-processing', id: 10, path: '/monitoring/file-processing', label: '文档全链路耗时', icon: Timer}];
const visibleMenus = computed(() => menus.flatMap(item => {
  if (!item.children) return allowedMenuIds.value === null || allowedMenuIds.value.includes(item.id!) ? [item] : [];
  const children = allowedMenuIds.value === null ? item.children : item.children.filter(child => allowedMenuIds.value!.includes(child.id!));
  return children.length ? [{...item, children}] : [];
}));
const isSystemRoute = computed(() => route.path.startsWith('/system/users') || route.path.startsWith('/system/depts') || route.path.startsWith('/system/roles') || route.path.startsWith('/system/menus'));
const systemFlyoutStyle = computed(() => ({top: `${systemFlyoutPosition.value.top}px`, left: `${systemFlyoutPosition.value.left}px`}));
const pageTitles: Record<string, string> = {
  '/dashboard': '首页',
  '/kb': '知识库管理',
  '/chat': '智能问答',
  '/system/users': '用户管理',
  '/system/depts': '部门管理',
  '/system/roles': '角色管理',
  '/system/menus': '菜单管理',
  '/audit': '审计日志',
  '/monitoring': 'RAGAS 评估',
  '/monitoring/file-processing': '文档全链路耗时',
  '/profile': '个人中心'
};
const titleForPath = (path: string) => path.startsWith('/kb/') ? '知识库详情' : (pageTitles[path] || '工作台');
const title = computed(() => titleForPath(route.path));
const defaultTabs = (): WorkspaceTab[] => [{path: '/dashboard', title: '首页', closable: false}];
const loadTabs = (): WorkspaceTab[] => {
  if (typeof localStorage === 'undefined') return defaultTabs();
  try {
    const stored = JSON.parse(localStorage.getItem(TAB_STORAGE_KEY) || '[]') as WorkspaceTab[];
    const unique = stored.filter((tab, index) => tab && typeof tab.path === 'string' && typeof tab.title === 'string' && stored.findIndex(item => item.path === tab.path) === index);
    return unique.some(tab => tab.path === '/dashboard') ? unique.map(tab => {
      if (tab.path === '/dashboard') return {...tab, title: '首页', closable: false};
      if (tab.path === '/system/users') return {...tab, title: '用户管理', closable: true};
      return {...tab, closable: true};
    }) : [...defaultTabs(), ...unique];
  } catch {
    return defaultTabs();
  }
};
const openTabs = ref<WorkspaceTab[]>(loadTabs());

function iconForPath(path: string) {
  if (path.startsWith('/kb/')) return FolderOpened;
  return menus.flatMap(item => item.children || [item]).find(item => item.path === path)?.icon || User;
}

function updateSystemFlyoutPosition() {
  const trigger = getSystemMenuTrigger();
  if (!trigger || typeof window === 'undefined') return;
  const rect = trigger.getBoundingClientRect();
  systemFlyoutPosition.value = {
    top: Math.max(12, Math.min(rect.top, window.innerHeight - 280)),
    left: Math.max(12, Math.min(rect.right + 8, window.innerWidth - 220)),
  };
}

function getSystemMenuTrigger() {
  const trigger = Array.isArray(systemMenuTrigger.value) ? systemMenuTrigger.value[0] : systemMenuTrigger.value;
  return trigger instanceof HTMLButtonElement ? trigger : undefined;
}

function getSystemFlyout() {
  const flyout = Array.isArray(systemFlyout.value) ? systemFlyout.value[0] : systemFlyout.value;
  return flyout instanceof HTMLElement ? flyout : undefined;
}

function cancelSystemFlyoutClose() {
  if (systemFlyoutCloseTimer) clearTimeout(systemFlyoutCloseTimer);
  systemFlyoutCloseTimer = undefined;
}

function openSystemFlyout(focusFirst = false) {
  if (!sidebarCollapsed.value || isMobileNav.value) return;
  cancelSystemFlyoutClose();
  updateSystemFlyoutPosition();
  systemFlyoutOpen.value = true;
  if (focusFirst) nextTick(() => getSystemFlyout()?.querySelector<HTMLAnchorElement>('a')?.focus());
}

function closeSystemFlyout() {
  cancelSystemFlyoutClose();
  systemFlyoutOpen.value = false;
}

function scheduleSystemFlyoutClose() {
  if (!systemFlyoutOpen.value) return;
  cancelSystemFlyoutClose();
  systemFlyoutCloseTimer = setTimeout(closeSystemFlyout, 160);
}

function toggleSystemMenu(event: MouseEvent) {
  if (sidebarCollapsed.value && !isMobileNav.value) {
    if (systemFlyoutOpen.value) closeSystemFlyout();
    else openSystemFlyout(event.detail === 0);
    return;
  }
  systemMenuExpanded.value = !systemMenuExpanded.value;
}

function updateMobileNavState() {
  isMobileNav.value = typeof window !== 'undefined' && window.innerWidth <= 760;
  if (isMobileNav.value) closeSystemFlyout();
  else updateSystemFlyoutPosition();
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target as Node | null;
  if (!target || getSystemMenuTrigger()?.contains(target) || getSystemFlyout()?.contains(target)) return;
  closeSystemFlyout();
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !systemFlyoutOpen.value) return;
  closeSystemFlyout();
  getSystemMenuTrigger()?.focus();
}

function ensureTab(path: string) {
  if (path === '/login' || openTabs.value.some(tab => tab.path === path)) return;
  openTabs.value.push({path, title: titleForPath(path), closable: path !== '/dashboard'});
}

function closeTab(path: string) {
  const index = openTabs.value.findIndex(tab => tab.path === path);
  if (index < 0 || !openTabs.value[index].closable) return;
  const wasCurrent = route.path === path;
  openTabs.value.splice(index, 1);
  if (wasCurrent) router.push(openTabs.value[Math.max(0, index - 1)]?.path || '/dashboard');
}

watch(() => route.path, ensureTab, {immediate: true});
watch(isSystemRoute, active => {
  if (active) systemMenuExpanded.value = true;
}, {immediate: true});
watch(openTabs, tabs => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(TAB_STORAGE_KEY, JSON.stringify(tabs));
}, {deep: true});
watch(isDark, dark => {
  document.documentElement.classList.toggle('dark', dark);
  try { localStorage.setItem(THEME_STORAGE_KEY, String(dark)); } catch { /* storage is optional */ }
}, {immediate: true});
watch(sidebarCollapsed, collapsed => {
  if (!collapsed) closeSystemFlyout();
  try { localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed)); } catch { /* storage is optional */ }
});

onMounted(async () => {
  updateMobileNavState();
  document.addEventListener('pointerdown', handleDocumentPointerDown);
  document.addEventListener('keydown', handleDocumentKeydown);
  window.addEventListener('resize', updateMobileNavState);
  try {
    const [{data: user}, {data: depts}, {data: permissions}] = await Promise.all([authApi.me(), systemApi.departments(), systemApi.myMenus()]);
    userName.value = user.realName || user.username;
    const dept = (depts as any[]).find(item => item.id === user.deptId);
    deptName.value = dept?.name || '未分配部门';
    allowedMenuIds.value = permissions.menuIds || [];
  } catch { /* optional profile data */ }
})

onBeforeUnmount(() => {
  cancelSystemFlyoutClose();
  document.removeEventListener('pointerdown', handleDocumentPointerDown);
  document.removeEventListener('keydown', handleDocumentKeydown);
  window.removeEventListener('resize', updateMobileNavState);
});

function logout() {
  localStorage.removeItem('rag_token');
  router.replace('/login')
}

function handleUserCommand(command: 'profile' | 'logout') {
  if (command === 'profile') router.push('/profile')
  if (command === 'logout') logout()
}
</script>
