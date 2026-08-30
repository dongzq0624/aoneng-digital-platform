<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">组织与权限</p>
        <h1>部门管理</h1>
        <p>维护部门层级，并控制员工归属。</p></div>
      <el-button type="primary" @click="openCreate(0)">新增部门</el-button>
    </div>
    <div class="panel" v-loading="loading">
      <el-tree v-if="tree.length" :data="tree" node-key="id" default-expand-all class="dept-tree">
        <template #default="{data}">
          <div class="dept-node"><span class="dept-name">{{ data.name }}</span><span
              class="dept-meta">{{ data.userCount || 0 }} 名员工</span><span class="dept-actions"><el-button link
                                                                                                             type="primary"
                                                                                                             @click.stop="openCreate(data.id)">新增下级</el-button><el-button
              link @click.stop="openEdit(data)">编辑</el-button><el-button link type="danger"
                                                                           @click.stop="remove(data)">删除</el-button></span>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无部门"/>
    </div>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑部门' : '新增部门'" width="440px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="部门名称" required>
          <el-input v-model="form.name" maxlength="64"/>
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" style="width:100%" placeholder="请选择上级部门">
            <el-option label="无（顶级部门）" :value="0"/>
            <el-option v-for="dept in parentOptions" :key="dept.id" :label="dept.label" :value="dept.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled"/>
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
import {systemApi, type Department} from '../../api'

interface TreeDept extends Department {
  children?: TreeDept[]
}

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editing = ref(false);
const rows = ref<Department[]>([])
const form = reactive<{ id?: number; name: string; parentId: number; sort: number; enabled: boolean }>({
  name: '',
  parentId: 0,
  sort: 0,
  enabled: true
})
const tree = computed<TreeDept[]>(() => {
  const map = new Map<number, TreeDept>();
  rows.value.forEach(d => map.set(d.id, {...d, children: []}));
  const roots: TreeDept[] = [];
  map.forEach(d => {
    const parent = d.parentId ? map.get(d.parentId) : undefined;
    if (parent) parent.children!.push(d); else roots.push(d)
  });
  return roots
})
const parentOptions = computed(() => {
  const result: Array<{ id: number; label: string }> = [];
  const visit = (items: TreeDept[], depth: number) => items.forEach(item => {
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
    const {data} = await systemApi.departments();
    rows.value = (Array.isArray(data) ? data : []).map((d: any) => ({
      ...d,
      parentId: d.parentId ?? d.parentid,
      userCount: d.userCount ?? d.usercount,
      childCount: d.childCount ?? d.childcount
    }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '部门加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate(parentId: number) {
  editing.value = false;
  Object.assign(form, {id: undefined, name: '', parentId, sort: 0, enabled: true});
  dialogVisible.value = true
}

function openEdit(row: TreeDept) {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    name: row.name,
    parentId: row.parentId || 0,
    sort: row.sort || 0,
    enabled: row.status !== 0
  });
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入部门名称');
    return
  }
  saving.value = true;
  try {
    const payload = {name: form.name.trim(), parentId: form.parentId, sort: form.sort, status: form.enabled ? 1 : 0};
    if (editing.value && form.id) await systemApi.updateDepartment(form.id, payload); else await systemApi.createDepartment(payload);
    dialogVisible.value = false;
    await load();
    ElMessage.success('部门保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '部门保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: TreeDept) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.name}”？部门下必须没有子部门和员工。`, '删除部门', {type: 'warning'});
    await systemApi.removeDepartment(row.id);
    await load();
    ElMessage.success('部门已删除')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || (error instanceof Error ? error.message : '部门删除失败'))
  }
}
</script>
<style scoped>
.dept-tree {
  padding: 8px
}

.dept-node {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 36px
}

.dept-name {
  font-weight: 600;
  min-width: 220px
}

.dept-meta {
  color: var(--muted);
  font-size: 12px
}

.dept-actions {
  margin-left: auto;
  display: flex;
  gap: 4px
}

@media (max-width: 760px) {
  .dept-node {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 6px;
  }

  .dept-name {
    min-width: 0;
    flex: 1 1 150px;
    overflow-wrap: anywhere;
  }

  .dept-actions {
    width: 100%;
    margin-left: 0;
    flex-wrap: wrap;
  }
}
</style>
