<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">组织与权限</p>
        <h1>菜单管理</h1>
        <p>维护平台导航层级、访问路径和权限标识。</p></div>
      <el-button type="primary" @click="openCreate(0)">新增菜单</el-button>
    </div>
    <div class="panel" v-loading="loading">
      <div class="menu-toolbar">
        <div><h3>菜单目录</h3><small>通过排序值控制显示顺序，删除前需解除角色授权</small></div>
        <span class="filter-count">共 {{ rows.length }} 项</span></div>
      <el-tree v-if="tree.length" :data="tree" node-key="id" default-expand-all class="menu-tree"
               :props="{label: 'name', children: 'children'}">
        <template #default="{data}">
          <div class="menu-node"><span class="menu-node-name">{{ data.name }}</span><code
              v-if="data.perms">{{ data.perms }}</code><span v-if="data.path" class="menu-path">{{ data.path }}</span>
            <el-tag size="small" :type="data.status === 1 ? 'success' : 'danger'">{{
                data.status === 1 ? '启用' : '停用'
              }}
            </el-tag>
            <span class="menu-sort">排序 {{ data.sort || 0 }}</span><span class="menu-actions"><el-button link
                                                                                                          type="primary"
                                                                                                          @click.stop="openCreate(data.id)">新增下级</el-button><el-button
                link @click.stop="openEdit(data)">编辑</el-button><el-button link type="danger"
                                                                             @click.stop="remove(data)">删除</el-button></span>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无菜单"/>
    </div>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新增菜单'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：项目管理"/>
        </el-form-item>
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" style="width:100%" placeholder="请选择上级菜单">
            <el-option label="无（顶级菜单）" :value="0"/>
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" maxlength="128" placeholder="例如：project:view"/>
        </el-form-item>
        <el-form-item label="访问路径">
          <el-input v-model="form.path" maxlength="255" placeholder="例如：/project"/>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999"/>
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
import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {systemApi, type MenuPermission} from '../../api'

interface TreeMenu extends MenuPermission {
  children?: TreeMenu[]
}

const rows = ref<MenuPermission[]>([]), loading = ref(false), saving = ref(false), dialogVisible = ref(false),
    editing = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  parentId: 0,
  perms: '',
  path: '',
  sort: 0,
  enabled: true
})
const tree = computed<TreeMenu[]>(() => {
  const map = new Map<number, TreeMenu>(), roots: TreeMenu[] = [];
  rows.value.forEach(item => map.set(item.id, {...item, children: []}));
  map.forEach(item => {
    const parent = item.parentId ? map.get(item.parentId) : undefined;
    if (parent) parent.children!.push(item); else roots.push(item)
  });
  return roots
})
const parentOptions = computed(() => {
  const result: Array<{ id: number; label: string }> = [];
  const visit = (items: TreeMenu[], depth: number) => items.forEach(item => {
    if (item.id !== form.id) {
      result.push({id: item.id, label: `${'　'.repeat(depth)}${item.name}`});
      visit(item.children || [], depth + 1)
    }
  });
  visit(tree.value, 0);
  return result
})

async function load() {
  loading.value = true;
  try {
    const {data} = await systemApi.menus();
    rows.value = data.map((item: any) => ({
      ...item,
      parentId: item.parentId ?? item.parentid,
      childCount: item.childCount ?? item.childcount,
      roleCount: item.roleCount ?? item.rolecount
    }))
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '菜单加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(parentId: number) {
  editing.value = false;
  Object.assign(form, {id: undefined, name: '', parentId, perms: '', path: '', sort: 0, enabled: true});
  dialogVisible.value = true
}

function openEdit(row: TreeMenu) {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    name: row.name,
    parentId: row.parentId || 0,
    perms: row.perms || '',
    path: row.path || '',
    sort: row.sort || 0,
    enabled: row.status !== 0
  });
  dialogVisible.value = true
}

async function submit() {
  form.name = form.name.trim();
  form.perms = form.perms.trim();
  form.path = form.path.trim();
  if (!form.name) return ElMessage.warning('请输入菜单名称');
  if (form.perms && !/^[a-zA-Z][\w:.\-]{1,127}$/.test(form.perms)) return ElMessage.warning('权限标识格式不正确');
  if (form.path && !form.path.startsWith('/')) return ElMessage.warning('访问路径必须以 / 开头');
  saving.value = true;
  try {
    const payload = {
      name: form.name,
      parentId: form.parentId,
      perms: form.perms || undefined,
      path: form.path || undefined,
      sort: form.sort,
      status: form.enabled ? 1 : 0
    };
    if (editing.value && form.id) await systemApi.updateMenu(form.id, payload); else await systemApi.createMenu(payload);
    dialogVisible.value = false;
    await load();
    ElMessage.success('菜单保存成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '菜单保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: TreeMenu) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.name}”？存在子菜单或角色授权时无法删除。`, '删除菜单', {type: 'warning'});
    await systemApi.removeMenu(row.id);
    await load();
    ElMessage.success('菜单已删除')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '菜单删除失败')
  }
}

onMounted(load)
</script>
<style scoped>
.menu-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 15px;
  border-bottom: 1px solid var(--line);
}

.menu-toolbar h3 {
  margin: 0;
  font-size: 15px;
}

.menu-toolbar small {
  display: block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}

.menu-tree {
  padding: 12px 4px;
}

.menu-node {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 38px;
  padding: 3px 0;
}

.menu-node-name {
  min-width: 130px;
  font-weight: 600;
  color: var(--ink);
}

.menu-node code {
  color: var(--teal-deep);
  font-size: 11px;
  background: var(--soft-green);
  padding: 3px 6px;
  border-radius: 4px;
}

.menu-path, .menu-sort {
  color: var(--muted);
  font-size: 11px;
}

.menu-actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}

@media (max-width: 760px) {
  .menu-node {
    flex-wrap: wrap;
    gap: 6px;
    padding: 6px 0;
  }

  .menu-node-name {
    min-width: 110px;
  }

  .menu-actions {
    width: 100%;
    margin-left: 0;
  }

  .menu-toolbar {
    align-items: flex-start;
    gap: 10px;
  }
}
</style>
