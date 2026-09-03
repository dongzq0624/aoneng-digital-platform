import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from './views/layout/AppLayout.vue';
import Login from './views/login/Login.vue';
import Dashboard from './views/dashboard/Dashboard.vue';
import KnowledgeBases from './views/kb/KnowledgeBases.vue';
import Chat from './views/chat/Chat.vue';
import Audit from './views/audit/Audit.vue';
import Users from './views/system/Users.vue';
import Departments from './views/system/Departments.vue';
import KnowledgeBaseDetail from './views/kb/KnowledgeBaseDetail.vue';
import Roles from './views/system/Roles.vue';
import Menus from './views/system/Menus.vue';
import Profile from './views/profile/Profile.vue';
import Monitoring from './views/monitoring/Monitoring.vue';
import FileProcessing from './views/monitoring/FileProcessing.vue';
import { systemApi } from './api';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: '/dashboard', component: Dashboard },
        { path: '/kb', component: KnowledgeBases },
        {
          path: '/kb/:id',
          component: KnowledgeBaseDetail,
        },
        { path: '/chat', component: Chat },
        { path: '/audit', component: Audit },
        { path: '/monitoring', component: Monitoring },
        { path: '/monitoring/file-processing', component: FileProcessing },
        { path: '/system/users', component: Users },
        { path: '/system/depts', component: Departments },
        { path: '/system/roles', component: Roles },
        { path: '/system/menus', component: Menus },
        { path: '/profile', component: Profile },
      ],
    },
  ],
});
const routePermission: Record<string, number> = {
  '/dashboard': 1,
  '/kb': 2,
  '/chat': 3,
  '/system/users': 5,
  '/system/depts': 6,
  '/system/roles': 7,
  '/system/menus': 8,
  '/audit': 9,
  '/monitoring': 10,
  '/monitoring/file-processing': 10,
};

router.beforeEach(async (to) => {
  if (to.path !== '/login' && !localStorage.getItem('rag_token'))
    return '/login';
  const matched = Object.keys(routePermission).find(
    (path) => to.path === path || (path === '/kb' && to.path.startsWith('/kb/'))
  );
  if (!matched) return true;
  try {
    const { data } = await systemApi.myMenus();
    if (data.menuIds.includes(routePermission[matched])) return true;
    return matched === '/dashboard' ? false : '/dashboard';
  } catch {
    return '/login';
  }
});
export default router;
