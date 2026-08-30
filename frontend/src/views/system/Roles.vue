<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">组织与权限</p>
        <h1>角色管理</h1>
        <p>创建岗位角色并配置可访问的菜单范围。</p></div>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>
    <div class="role-layout">
      <section class="panel role-list-panel" v-loading="loading">
        <div class="panel-title">
          <div><h3>角色列表</h3><small>共 {{ roles.length }} 个角色</small></div>
        </div>
        <el-table :data="roles" class="audit-table role-table" highlight-current-row @current-change="selectRole"
                  empty-text="暂无角色">
          <el-table-column prop="name" label="角色名称" min-width="140"/>
          <el-table-column prop="code" label="编码" min-width="150"/>
          <el-table-column label="菜单" width="72">
            <template #default="{row}">{{ row.menuCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="76">
            <template #default="{row}">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{
                  row.status === 1 ? '启用' : '停用'
                }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="132">
            <template #default="{row}">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
      <section class="panel role-permission-panel" v-loading="permissionLoading">
        <div class="panel-title">
          <div><h3>{{ selected ? `${selected.name} · 菜单权限` : '菜单权限' }}</h3>
            <small>{{ selected ? '勾选后保存该角色可访问的菜单' : '请选择左侧角色' }}</small></div>
          <el-button v-if="selected" type="primary" size="small" :loading="permissionSaving" @click="savePermissions">
            保存权限
          </el-button>
        </div>
        <el-tree v-if="selected && menuTree.length" ref="treeRef" :data="menuTree" node-key="id" show-checkbox
                 default-expand-all :props="{label: 'name', children: 'children'}" class="permission-tree"
                 empty-text="暂无菜单"/>
        <el-empty v-else-if="selected" description="暂无可配置菜单"/>
        <el-empty v-else description="请选择一个角色"/>
      </section>
    </div>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="86px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：项目负责人"/>
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" :disabled="editing" maxlength="64" placeholder="例如：PROJECT_OWNER"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {systemApi, type MenuPermission, type Role} from '../../api'

interface TreeMenu extends MenuPermission {
  children?: TreeMenu[]
}

const roles = ref<Role[]>([]), menus = ref<MenuPermission[]>([]), selected = ref<Role>();
const loading = ref(false), permissionLoading = ref(false), permissionSaving = ref(false), saving = ref(false),
    dialogVisible = ref(false), editing = ref(false);
const treeRef = ref<any>();
const form = reactive({id: undefined as number | undefined, name: '', code: '', remark: '', enabled: true});
const menuTree = computed<TreeMenu[]>(() => {
  const map = new Map<number, TreeMenu>();
  const roots: TreeMenu[] = [];
  menus.value.forEach(m => map.set(m.id, {...m, children: []}));
  map.forEach(item => {
    const parent = item.parentId ? map.get(item.parentId) : undefined;
    if (parent) parent.children!.push(item); else roots.push(item)
  });
  return roots
});

async function load() {
  loading.value = true;
  try {
    const [roleRes, menuRes] = await Promise.all([systemApi.roles(), systemApi.menus()]);
    roles.value = roleRes.data;
    menus.value = menuRes.data.map((m: any) => ({
      ...m,
      parentId: m.parentId ?? m.parentid,
      childCount: m.childCount ?? m.childcount,
      roleCount: m.roleCount ?? m.rolecount
    }));
    if (selected.value) {
      selected.value = roles.value.find(r => r.id === selected.value!.id);
      if (selected.value) await selectRole(selected.value)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '角色和菜单加载失败')
  } finally {
    loading.value = false
  }
}

async function selectRole(role?: Role) {
  selected.value = role;
  if (!role) return;
  permissionLoading.value = true;
  try {
    const {data} = await systemApi.roleMenus(role.id);
    await nextTick();
    treeRef.value?.setCheckedKeys(data.menuIds || [])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '角色权限加载失败')
  } finally {
    permissionLoading.value = false
  }
}

function openCreate() {
  editing.value = false;
  Object.assign(form, {id: undefined, name: '', code: '', remark: '', enabled: true});
  dialogVisible.value = true
}

function openEdit(role: Role) {
  editing.value = true;
  Object.assign(form, {
    id: role.id,
    name: role.name,
    code: role.code,
    remark: role.remark || '',
    enabled: role.status !== 0
  });
  dialogVisible.value = true
}

async function submit() {
  form.name = form.name.trim();
  form.code = form.code.trim().toUpperCase();
  if (!form.name || !form.code) return ElMessage.warning('请填写角色名称和编码');
  if (!/^[A-Z][A-Z0-9_]{2,63}$/.test(form.code)) return ElMessage.warning('角色编码须为 3-64 位大写字母、数字或下划线');
  saving.value = true;
  try {
    const payload = {name: form.name, code: form.code, remark: form.remark.trim(), status: form.enabled ? 1 : 0};
    const {data} = editing.value && form.id ? await systemApi.updateRole(form.id, payload) : await systemApi.createRole(payload);
    dialogVisible.value = false;
    await load();
    const created = roles.value.find(r => r.id === data.id);
    if (created) await selectRole(created);
    ElMessage.success('角色保存成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '角色保存失败')
  } finally {
    saving.value = false
  }
}

async function savePermissions() {
  if (!selected.value) return;
  permissionSaving.value = true;
  try {
    const keys = (treeRef.value?.getCheckedKeys() || []) as number[];
    await systemApi.updateRoleMenus(selected.value.id, keys);
    await load();
    ElMessage.success('菜单权限已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '权限保存失败')
  } finally {
    permissionSaving.value = false
  }
}

async function remove(role: Role) {
  try {
    await ElMessageBox.confirm(`确认删除“${role.name}”？已关联用户的角色无法删除。`, '删除角色', {type: 'warning'});
    await systemApi.removeRole(role.id);
    if (selected.value?.id === role.id) selected.value = undefined;
    await load();
    ElMessage.success('角色已删除')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '角色删除失败')
  }
}

onMounted(load)
</script>
<style scoped>
.role-layout {
  display: grid;
  grid-template-columns:minmax(0, 1fr) minmax(340px, .85fr);
  gap: 16px;
  align-items: start;
}

.role-list-panel, .role-permission-panel {
  min-height: 450px;
}

.role-table {
  margin-top: 16px;
}

.permission-tree {
  margin-top: 18px;
  padding: 8px 4px;
}

@media (max-width: 900px) {
  .role-layout {
    grid-template-columns:1fr;
  }

  .role-list-panel, .role-permission-panel {
    min-height: unset;
  }
}
</style>
