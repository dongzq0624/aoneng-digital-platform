<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">知识资产中心</p>
        <h1>知识库管理</h1>
        <p>集中管理组织知识，打造可信赖的信息源。</p></div>
      <el-button type="primary" :icon="Plus" @click="create">新建知识库</el-button>
    </div>
    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="搜索知识库" clearable style="width:260px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="visibility" placeholder="可见范围" clearable style="width:150px">
        <el-option label="全部范围" value=""/>
        <el-option label="全公司" value="ORG"/>
        <el-option label="本部门" value="DEPT"/>
        <el-option label="仅自己" value="PRIVATE"/>
      </el-select>
      <span class="filter-count">共 {{ filtered.length }} 个知识库</span></div>
    <div class="kb-cards">
      <div class="kb-card" v-for="kb in filtered" :key="kb.id">
        <div class="kb-card-top"><span class="large-kb-icon">{{ kb.category }}</span>
          <el-dropdown v-if="kb.canManage">
            <button class="more-btn" type="button" aria-label="知识库更多操作"><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="edit(kb)">编辑</el-dropdown-item>
                <el-dropdown-item v-if="kb.canConfigureDepartments" @click="openDepartmentAccess(kb)">配置允许部门</el-dropdown-item>
                <el-dropdown-item @click="remove(kb)" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <h3>{{ kb.name }}</h3>
        <p>{{ kb.description }}</p>
        <div class="kb-meta"><span><el-icon aria-hidden="true"><Document /></el-icon>{{
            kb.docCount ?? (kb as any).doccount ?? 0
          }} 篇文档</span><span><el-icon aria-hidden="true"><Clock /></el-icon>{{ kb.updatedAt || (kb as any).updatedat }}</span></div>
        <div class="kb-foot">
          <el-tag size="small" effect="plain">{{ label(kb.visibility) }}</el-tag>
          <RouterLink :to="`/kb/${kb.id}`">进入知识库 →</RouterLink>
        </div>
      </div>
    </div>
    <el-empty v-if="!filtered.length" description="暂无匹配知识库"/>
    <el-dialog v-model="departmentDialogVisible" title="配置允许访问部门" width="480px" destroy-on-close>
      <div class="department-access-intro">
        <b>{{ configuringBase?.name }}</b>
        <p>仅所选部门的已授权成员可访问该知识库。组织级和公开知识库未选择部门时对所有已授权部门开放。</p>
      </div>
      <el-checkbox-group v-model="selectedDepartmentIds" class="department-access-list">
        <el-checkbox v-for="department in departmentOptions" :key="department.id" :value="department.id">{{ department.label }}</el-checkbox>
      </el-checkbox-group>
      <template #footer><el-button @click="departmentDialogVisible=false">取消</el-button><el-button type="primary" :loading="departmentSaving" @click="saveDepartmentAccess">保存部门权限</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">import {computed, onMounted, ref} from 'vue';
import {Clock, Document, MoreFilled, Plus, Search} from '@element-plus/icons-vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import type {KnowledgeBase} from '../../types';
import {knowledgeBaseApi, systemApi, type Department} from '../../api';

const keyword = ref('');
const visibility = ref('');
const list = ref<KnowledgeBase[]>([]);
const departments = ref<Department[]>([]);
const departmentDialogVisible = ref(false);
const departmentSaving = ref(false);
const configuringBase = ref<KnowledgeBase>();
const selectedDepartmentIds = ref<number[]>([]);
const docCountOf = (kb: any) => kb.docCount ?? kb.doccount ?? 0;
const updatedAtOf = (kb: any) => kb.updatedAt || kb.updatedat || '-';
const filtered = computed(() => list.value.filter(k => (!keyword.value || k.name.includes(keyword.value)) && (!visibility.value || k.visibility === visibility.value)));
const label = (v: string) => ({PRIVATE: '仅自己', DEPT: '本部门', ORG: '全公司', PUBLIC: '公开'}[v] || v);
const departmentOptions = computed(() => {
  const grouped = new Map<number, Department[]>();
  departments.value.forEach(item => { const parent = item.parentId || 0; grouped.set(parent, [...(grouped.get(parent) || []), item]) });
  const output: Array<{id: number; label: string}> = [];
  const visit = (parentId: number, level: number) => (grouped.get(parentId) || []).forEach(item => { output.push({id: item.id, label: `${'　'.repeat(level)}${item.name}`}); visit(item.id, level + 1) });
  visit(0, 0);
  return output
});

onMounted(async () => {
  try {
    const [{data}, departmentResponse] = await Promise.all([knowledgeBaseApi.list(), systemApi.departments()]);
    const items: any[] = Array.isArray(data) ? data : data.items
    list.value = items.map(item => ({
      ...item,
      ownerId: item.ownerId ?? item.ownerid,
      deptId: item.deptId ?? item.deptid,
      chunkSize: item.chunkSize ?? item.chunksize,
      chunkOverlap: item.chunkOverlap ?? item.chunkoverlap,
      updatedAt: item.updatedAt || item.updatedat,
      docCount: item.docCount ?? item.doccount,
      canManage: item.canManage ?? item.canmanage,
      canConfigureDepartments: item.canConfigureDepartments ?? item.canconfiguredepartments,
      allowedDeptIds: item.allowedDeptIds ?? item.alloweddeptids ?? []
    }))
    departments.value = (Array.isArray(departmentResponse.data) ? departmentResponse.data : []).map((item: any) => ({...item, parentId: item.parentId ?? item.parentid}))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库加载失败')
  }
})

async function create() {
  try {
    const result = await ElMessageBox.prompt('请输入知识库名称', '新建知识库', {inputPlaceholder: '例如：销售知识库'});
    const name = result.value?.trim();
    if (!name) return;
    const {data} = await knowledgeBaseApi.create({name, visibility: 'DEPT'});
    list.value.unshift({...data, description: '', category: '✦', docCount: 0, updatedAt: '刚刚'} as KnowledgeBase);
    ElMessage.success('知识库创建成功')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '创建失败')
  }
}

async function openDepartmentAccess(kb: KnowledgeBase) {
  configuringBase.value = kb;
  selectedDepartmentIds.value = [...(kb.allowedDeptIds || [])];
  try {
    const {data} = await knowledgeBaseApi.allowedDepartments(kb.id);
    selectedDepartmentIds.value = data.departmentIds || [];
    departmentDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '部门权限加载失败')
  }
}

async function saveDepartmentAccess() {
  const kb = configuringBase.value;
  if (!kb) return;
  if (kb.visibility === 'DEPT' && !selectedDepartmentIds.value.length) {
    ElMessage.warning('部门知识库至少需要选择一个允许访问的部门');
    return
  }
  departmentSaving.value = true;
  try {
    const {data} = await knowledgeBaseApi.updateAllowedDepartments(kb.id, selectedDepartmentIds.value);
    const index = list.value.findIndex(item => item.id === kb.id);
    if (index >= 0) list.value[index] = {...list.value[index], ...data, allowedDeptIds: (data as any).allowedDeptIds ?? (data as any).alloweddeptids ?? []};
    departmentDialogVisible.value = false;
    ElMessage.success('允许访问部门已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '部门权限保存失败')
  } finally {
    departmentSaving.value = false
  }
}

async function edit(k: KnowledgeBase) {
  try {
    const result = await ElMessageBox.prompt('请输入新的知识库名称', '编辑知识库', {inputValue: k.name})
    const name = result.value?.trim();
    if (!name) return
    const {data} = await knowledgeBaseApi.update(k.id, {name})
    const index = list.value.findIndex(item => item.id === k.id)
    if (index >= 0) list.value[index] = {...list.value[index], ...data}
    ElMessage.success('知识库已更新')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '更新失败')
  }
}

async function remove(k: KnowledgeBase) {
  await ElMessageBox.confirm(`确认删除“${k.name}”？`, '删除知识库', {type: 'warning'});
  try {
    await knowledgeBaseApi.remove(k.id);
    list.value = list.value.filter(x => x.id !== k.id);
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}</script>
<style scoped>
.department-access-intro {
  padding: 12px 14px;
  background: var(--soft-green);
  border-radius: 6px;
  color: var(--ink)
}
.department-access-intro b { font-size: 13px }
.department-access-intro p { margin: 6px 0 0; color: var(--muted); font-size: 12px; line-height: 1.6 }
.department-access-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px }
@media (max-width: 520px) { .department-access-list { grid-template-columns: 1fr } }
</style>
