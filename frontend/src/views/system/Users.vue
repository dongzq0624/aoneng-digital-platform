<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">组织与权限</p>
        <h1>用户管理</h1>
        <p>管理企业账号、部门归属与角色权限。</p></div>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>
    <div class="filter-bar user-filter">
      <el-input v-model="keyword" placeholder="搜索姓名 / 工号" clearable style="width:260px"/>
      <el-button type="primary" plain @click="applyFilters">查询</el-button>
      <span class="filter-count">共 {{ total }} 位员工</span></div>
    <el-table :data="rows" class="audit-table" v-loading="loading">
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowNumber" />
      <el-table-column label="员工" min-width="220">
        <template #default="{row}">
          <div class="table-user"><span>{{ row.name.slice(0, 1) }}</span>
            <div><b>{{ row.name }}</b><small>{{ row.employeeNo }}</small></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="dept" label="所属部门"/>
      <el-table-column prop="role" label="角色"/>
      <el-table-column label="状态">
        <template #default="{row}">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{
              row.status === 1 ? '正常' : '停用'
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link @click="reset(row)">重置密码</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-pagination">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                     :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
                     @current-change="load" @size-change="handleSizeChange" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新增用户'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" maxlength="64"/>
        </el-form-item>
        <el-form-item label="工号" required>
          <el-input v-model="form.employeeNo" :disabled="editing" maxlength="32"/>
        </el-form-item>
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="editing" maxlength="64"/>
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="form.deptId" placeholder="请选择部门" style="width:100%">
            <el-option v-for="dept in departmentOptions" :key="dept.id" :label="dept.label" :value="dept.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {systemApi, type Department} from '../../api'

interface UserRow {
  id: number;
  name: string;
  employeeNo: string;
  username: string;
  dept: string;
  deptId?: number;
  role: string;
  status: number
}

interface UserForm {
  id?: number;
  realName: string;
  employeeNo: string;
  username: string;
  deptId?: number;
  enabled: boolean
}

const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editing = ref(false)
const rows = ref<UserRow[]>([]);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const departments = ref<Department[]>([])
const form = reactive<UserForm>({realName: '', employeeNo: '', username: '', deptId: undefined, enabled: true})
const filtered = computed(() => rows.value)
const departmentOptions = computed(() => {
  const byParent = new Map<number, Department[]>();
  departments.value.forEach(d => {
    const key = d.parentId ?? 0;
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key)!.push(d)
  })
  const output: Array<{ id: number; label: string }> = [];
  const visit = (parent: number, depth: number) => {
    (byParent.get(parent) || []).forEach(d => {
      output.push({id: d.id, label: `${'　'.repeat(depth)}${d.name}`});
      visit(d.id, depth + 1)
    })
  };
  visit(0, 0);
  return output
})

async function load() {
  loading.value = true
  try {
    const [users, depts] = await Promise.all([systemApi.users({keyword: keyword.value || undefined, page: page.value, pageSize: pageSize.value}), systemApi.departments()])
    const payload: any = Array.isArray(users.data) ? {items: users.data, total: users.data.length} : users.data
    const items: any[] = payload.items || []
    const departmentItems = (Array.isArray(depts.data) ? depts.data : []).map((d: any) => ({
      ...d,
      parentId: d.parentId ?? d.parentid
    })) as Department[]
    const departmentById = new Map(departmentItems.map(dept => [Number(dept.id), dept.name]))
    total.value = Number(payload.total ?? items.length)
    rows.value = items.map(item => ({
      id: item.id,
      name: item.name || item.realName || item.realname,
      employeeNo: item.employeeNo || item.employeeno,
      username: item.username,
      dept: departmentName(item, departmentById),
      deptId: item.deptId ?? item.deptid,
      role: item.role || '普通员工',
      status: item.status ?? 1
    }))
    departments.value = departmentItems
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户和部门加载失败')
  } finally {
    loading.value = false
  }
}

function departmentName(item: any, departmentById: Map<number, string>): string {
  const deptId = item.deptId ?? item.deptid
  const rawDept = item.dept ?? item.deptName ?? item.deptname ?? item.departmentName ?? item.departmentname
  if (typeof rawDept === 'string' && rawDept.trim()) return rawDept.trim()
  if (rawDept && typeof rawDept === 'object' && typeof rawDept.name === 'string' && rawDept.name.trim()) {
    return rawDept.name.trim()
  }
  const mapped = deptId == null ? undefined : departmentById.get(Number(deptId))
  return mapped || (deptId == null ? '未分配部门' : `部门 ${deptId}`)
}

function applyFilters() { page.value = 1; load() }
function handleSizeChange(size: number) { pageSize.value = size; page.value = 1; load() }
function rowNumber(index: number) { return (page.value - 1) * pageSize.value + index + 1 }

onMounted(load)

function openCreate() {
  editing.value = false;
  Object.assign(form, {
    id: undefined,
    realName: '',
    employeeNo: `E${Date.now().toString().slice(-6)}`,
    username: `user${Date.now().toString().slice(-6)}`,
    deptId: undefined,
    enabled: true
  });
  dialogVisible.value = true
}

function openEdit(row: UserRow) {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    realName: row.name,
    employeeNo: row.employeeNo,
    username: row.username,
    deptId: row.deptId,
    enabled: row.status === 1
  });
  dialogVisible.value = true
}

async function submitUser() {
  if (!form.realName.trim() || !form.employeeNo.trim() || !form.username.trim() || !form.deptId) {
    ElMessage.warning('请完整填写姓名、工号、账号和所属部门');
    return
  }
  saving.value = true
  try {
    if (editing.value && form.id) await systemApi.updateUser(form.id, {
      realName: form.realName.trim(),
      deptId: form.deptId,
      status: form.enabled ? 1 : 0
    }); else await systemApi.createUser({
      realName: form.realName.trim(),
      employeeNo: form.employeeNo.trim(),
      username: form.username.trim(),
      deptId: form.deptId,
      status: form.enabled ? 1 : 0
    });
    dialogVisible.value = false;
    await load();
    ElMessage.success('用户保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户保存失败')
  } finally {
    saving.value = false
  }
}

async function reset(row: UserRow) {
  try {
    await ElMessageBox.confirm(`确认重置“${row.name}”的密码？`, '重置密码', {type: 'warning'});
    await systemApi.resetPassword(row.id);
    ElMessage.success('密码已重置')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '密码重置失败')
  }
}

async function remove(row: UserRow) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.name}”？`, '删除用户', {type: 'warning'});
    await systemApi.removeUser(row.id);
    await load();
    ElMessage.success('用户已删除')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '用户删除失败')
  }
}
</script>
