<template>
  <div class="knowledge-base-page">
    <div class="kb-hero">
      <div class="kb-hero-grid" aria-hidden="true"></div>
      <div class="page-head compact kb-page-head">
      <div class="kb-head-copy"><p class="eyebrow">知识资产中心 · KNOWLEDGE HUB</p>
        <h1>知识库管理</h1>
        <p>集中管理组织知识，打造可信赖的信息源。</p></div>
      <div class="kb-head-actions">
        <span class="kb-live-indicator"><i></i>实时知识资产</span>
        <el-button type="primary" :icon="Plus" @click="create">新建知识库</el-button>
      </div>
      </div>
      <div class="kb-hero-stats" aria-label="知识库概览">
        <div class="kb-hero-stat"><span>知识库</span><strong>{{ filtered.length }}</strong><small>个可访问空间</small></div>
        <div class="kb-hero-stat"><span>文档总量</span><strong>{{ totalDocuments }}</strong><small>份已纳入索引</small></div>
        <div class="kb-hero-stat"><span>我可管理</span><strong>{{ manageableCount }}</strong><small>个协作空间</small></div>
      </div>
    </div>
    <div class="filter-bar kb-filter-bar">
      <el-input v-model="keyword" placeholder="搜索知识库" clearable style="width:260px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="visibility" placeholder="可见范围" clearable style="width:150px">
        <el-option label="全部范围" value=""/>
        <el-option label="全公司" value="ORG"/>
        <el-option label="允许部门" value="DEPT"/>
        <el-option label="仅自己" value="PRIVATE"/>
      </el-select>
      <span class="filter-count"><i class="kb-filter-dot"></i>共 {{ filtered.length }} 个知识库</span></div>
    <div class="kb-cards">
      <div class="kb-card" v-for="kb in filtered" :key="kb.id">
        <span class="kb-card-accent" aria-hidden="true"></span>
        <div class="kb-card-top"><span class="large-kb-icon">{{ kb.category }}</span>
          <el-dropdown v-if="kb.canManage">
            <button class="more-btn" type="button" aria-label="知识库更多操作"><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="edit(kb)">编辑</el-dropdown-item>
                <el-dropdown-item @click="remove(kb)" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <h3>{{ kb.name }}</h3>
        <p>{{ kb.description }}</p>
        <div class="kb-meta"><span><el-icon aria-hidden="true"><Document /></el-icon>{{
            kb.docCount ?? (kb as any).doccount ?? 0
          }} 篇文档</span><span><el-icon aria-hidden="true"><Clock /></el-icon>{{ formatBeijingTime(kb.updatedAt || (kb as any).updatedat) }}</span></div>
        <div class="kb-foot">
          <el-tag size="small" effect="plain">{{ label(kb.visibility) }}</el-tag>
          <RouterLink :to="`/kb/${kb.id}`">进入知识库 →</RouterLink>
        </div>
      </div>
    </div>
    <el-empty v-if="!filtered.length" description="暂无匹配知识库"/>
    <el-dialog v-model="createDialogVisible" :title="dialogTitle" width="560px" destroy-on-close align-center>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px" @submit.prevent>
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="createForm.name" maxlength="128" show-word-limit placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入知识库用途和内容说明" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="createForm.category" maxlength="64" placeholder="例如：研发、人事行政" />
        </el-form-item>
        <el-form-item label="可见范围" prop="visibility">
          <el-select v-model="createForm.visibility" style="width: 100%" placeholder="请选择可见范围">
            <el-option label="仅自己" value="PRIVATE" />
            <el-option label="允许部门" value="DEPT" />
            <el-option label="全公司" value="ORG" />
            <el-option label="公开" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <div v-if="createForm.visibility === 'DEPT'" class="department-access-section">
          <div class="department-access-intro">
            <b>允许访问部门</b>
            <p v-if="canConfigureDepartments">可多选部门，只有所选部门的已授权成员可以访问该知识库。</p>
            <p v-else>仅系统管理员可调整部门权限，当前授权部门保持不变。</p>
          </div>
          <el-checkbox-group v-model="selectedDepartmentIds" class="department-access-list"
                             :disabled="!canConfigureDepartments">
            <el-checkbox v-for="department in departmentOptions" :key="department.id" :value="department.id"
                         :class="['department-option', {'is-parent': department.hasChildren, 'is-child': department.level > 0}]"
                         :style="{'--department-level': department.level}"
                         @change="handleDepartmentChange(department.id, $event)">
              {{ department.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">{{ dialogSubmitLabel }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">import {computed, onMounted, reactive, ref} from 'vue';
import {Clock, Document, MoreFilled, Plus, Search} from '@element-plus/icons-vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import type {KnowledgeBase} from '../../types';
import {knowledgeBaseApi, systemApi, type Department} from '../../api';
import {formatBeijingTime} from '../../utils/datetime';

const keyword = ref('');
const visibility = ref('');
const list = ref<KnowledgeBase[]>([]);
const departments = ref<Department[]>([]);
const selectedDepartmentIds = ref<number[]>([]);
const createDialogVisible = ref(false);
const createSaving = ref(false);
const editingBase = ref<KnowledgeBase | null>(null);
const createFormRef = ref<any>();
const createForm = reactive({
  name: '',
  description: '',
  category: '',
  visibility: 'DEPT' as KnowledgeBase['visibility']
});
const createRules = {
  name: [
    {required: true, message: '请输入知识库名称', trigger: 'blur'},
    {min: 2, max: 128, message: '名称长度需为 2-128 个字符', trigger: 'blur'}
  ],
  description: [{required: true, message: '请输入知识库描述', trigger: 'blur'}],
  category: [{required: true, message: '请输入知识库分类', trigger: 'blur'}],
  visibility: [{required: true, message: '请选择可见范围', trigger: 'change'}]
};
const docCountOf = (kb: any) => kb.docCount ?? kb.doccount ?? 0;
const updatedAtOf = (kb: any) => kb.updatedAt || kb.updatedat || '-';
const filtered = computed(() => list.value.filter(k => (!keyword.value || k.name.includes(keyword.value)) && (!visibility.value || k.visibility === visibility.value)));
const label = (v: string) => ({PRIVATE: '仅自己', DEPT: '允许部门', ORG: '全公司', PUBLIC: '公开'}[v] || v);
const dialogTitle = computed(() => editingBase.value ? '编辑知识库' : '新建知识库');
const dialogSubmitLabel = computed(() => editingBase.value ? '保存修改' : '创建知识库');
const totalDocuments = computed(() => filtered.value.reduce((total, kb) => {
  const count = Number(docCountOf(kb));
  return total + (Number.isFinite(count) ? count : 0);
}, 0));
const manageableCount = computed(() => filtered.value.filter(kb => kb.canManage).length);
const departmentOptions = computed(() => {
  const grouped = new Map<number, Department[]>();
  departments.value.forEach(item => {
    const parent = Number(item.parentId) || 0;
    grouped.set(parent, [...(grouped.get(parent) || []), item]);
  });
  grouped.forEach(items => items.sort((left, right) => {
    const sortDiff = (Number(left.sort) || 0) - (Number(right.sort) || 0);
    return sortDiff || left.id - right.id;
  }));
  const output: Array<{id: number; name: string; level: number; hasChildren: boolean}> = [];
  const visited = new Set<number>();
  const visit = (parentId: number, level: number) => (grouped.get(parentId) || []).forEach(item => {
    if (visited.has(item.id)) return;
    visited.add(item.id);
    output.push({id: item.id, name: item.name, level, hasChildren: Boolean(grouped.get(item.id)?.length)});
    visit(item.id, level + 1);
  });
  visit(0, 0);
  // Keep malformed/orphaned records visible without losing the single-column layout.
  departments.value.forEach(item => {
    if (visited.has(item.id)) return;
    output.push({id: item.id, name: item.name, level: 0, hasChildren: Boolean(grouped.get(item.id)?.length)});
  });
  return output
});
const canConfigureDepartments = computed(() => editingBase.value?.canConfigureDepartments
  ?? list.value.some(item => item.canConfigureDepartments));

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
    departments.value = (Array.isArray(departmentResponse.data) ? departmentResponse.data : []).map((item: any) => ({
      ...item,
      id: Number(item.id),
      parentId: Number(item.parentId ?? item.parentid) || 0,
      sort: Number(item.sort) || 0,
    }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库加载失败')
  }
})

function resetCreateForm() {
  editingBase.value = null;
  selectedDepartmentIds.value = [];
  createForm.name = '';
  createForm.description = '';
  createForm.category = '';
  createForm.visibility = 'DEPT';
  createFormRef.value?.clearValidate?.();
}

function create() {
  resetCreateForm();
  createDialogVisible.value = true;
}

async function submitCreate() {
  const form = createFormRef.value;
  if (!form) return;
  const valid = await form.validate().catch(() => false);
  if (!valid) return;
  if (canConfigureDepartments.value && createForm.visibility === 'DEPT' && !selectedDepartmentIds.value.length) {
    ElMessage.warning('部门知识库至少需要选择一个允许访问的部门');
    return;
  }

  createSaving.value = true;
  try {
    const payload = {
      name: createForm.name.trim(),
      description: createForm.description.trim(),
      category: createForm.category.trim(),
      visibility: createForm.visibility,
    };
    // Legacy department knowledge bases created by an administrator can have
    // no compatibility dept_id. Persist the selected departments first so the
    // backend can derive that field without trusting a client-supplied deptId.
    const existingDeptId = Number((editingBase.value as any)?.deptId);
    const syncBeforeUpdate = Boolean(editingBase.value)
      && canConfigureDepartments.value
      && createForm.visibility === 'DEPT'
      && !(existingDeptId > 0);
    if (syncBeforeUpdate) {
      await knowledgeBaseApi.updateAllowedDepartments(
        editingBase.value!.id,
        selectedDepartmentIds.value,
      );
    }
    const {data: baseData} = editingBase.value
      ? await knowledgeBaseApi.update(editingBase.value.id, payload)
      : await knowledgeBaseApi.create(payload);
    let item: any = baseData;
    const canSyncDepartments = Boolean(editingBase.value?.canConfigureDepartments)
      || Boolean(item.canConfigureDepartments ?? item.canconfiguredepartments);
    if (canSyncDepartments && !syncBeforeUpdate) {
      const {data} = await knowledgeBaseApi.updateAllowedDepartments(
        item.id,
        createForm.visibility === 'DEPT' ? selectedDepartmentIds.value : [],
      );
      item = data;
    }
    const normalized = {
      ...item,
      ownerId: item.ownerId ?? item.ownerid,
      deptId: item.deptId ?? item.deptid,
      chunkSize: item.chunkSize ?? item.chunksize,
      chunkOverlap: item.chunkOverlap ?? item.chunkoverlap,
      docCount: item.docCount ?? item.doccount ?? 0,
      updatedAt: item.updatedAt || item.updatedat || new Date().toISOString(),
      canManage: item.canManage ?? item.canmanage,
      canConfigureDepartments: item.canConfigureDepartments ?? item.canconfiguredepartments,
      allowedDeptIds: item.allowedDeptIds ?? item.alloweddeptids ?? []
    } as KnowledgeBase;
    if (editingBase.value) {
      const index = list.value.findIndex(entry => entry.id === editingBase.value?.id);
      if (index >= 0) list.value[index] = {...list.value[index], ...normalized};
    } else {
      list.value.unshift(normalized);
    }
    createDialogVisible.value = false;
    ElMessage.success(editingBase.value ? '知识库已更新' : '知识库创建成功');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败');
  } finally {
    createSaving.value = false;
  }
}

async function loadDepartmentSelection(kb: KnowledgeBase) {
  selectedDepartmentIds.value = normalizeDepartmentSelection(
    (kb.allowedDeptIds || []).map(id => Number(id)),
  );
  if (!kb.canConfigureDepartments) return;
  try {
    const {data} = await knowledgeBaseApi.allowedDepartments(kb.id);
    selectedDepartmentIds.value = normalizeDepartmentSelection(
      (data.departmentIds || []).map((id: number | string) => Number(id)),
    );
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '部门权限加载失败')
  }
}

const departmentById = computed(() => new Map(departments.value.map(department => [department.id, department])));

function directChildren(departmentId: number) {
  return departments.value.filter(department => (Number(department.parentId) || 0) === departmentId);
}

function descendantIds(departmentId: number) {
  const result: number[] = [];
  const visited = new Set<number>();
  const visit = (parentId: number) => {
    directChildren(parentId).forEach(child => {
      if (visited.has(child.id)) return;
      visited.add(child.id);
      result.push(child.id);
      visit(child.id);
    });
  };
  visit(departmentId);
  return result;
}

function normalizeDepartmentSelection(ids: number[]) {
  const selected = new Set(ids.filter(id => Number.isFinite(id) && id > 0));
  // A selected parent represents access to its complete subtree.
  [...selected].forEach(id => descendantIds(id).forEach(childId => selected.add(childId)));
  // Recalculate ancestor states from the leaves up using the same cascade rules.
  const ordered = [...departments.value].sort((left, right) => {
    const depth = (id: number) => {
      let value = 0;
      let current = departmentById.value.get(id);
      const seen = new Set<number>();
      while (current && current.parentId > 0 && !seen.has(current.id)) {
        seen.add(current.id);
        value++;
        current = departmentById.value.get(Number(current.parentId));
      }
      return value;
    };
    return depth(right.id) - depth(left.id);
  });
  ordered.forEach(department => {
    const children = directChildren(department.id);
    if (!children.length) return;
    const allChildrenSelected = children.every(child => selected.has(child.id));
    if (children.length === 1 || allChildrenSelected) selected.add(department.id);
    else selected.delete(department.id);
  });
  return [...selected];
}

function handleDepartmentChange(departmentId: number, checked: boolean) {
  const selected = new Set(selectedDepartmentIds.value.map(Number));
  const descendants = descendantIds(departmentId);
  if (checked) {
    selected.add(departmentId);
    descendants.forEach(id => selected.add(id));
  } else {
    selected.delete(departmentId);
    descendants.forEach(id => selected.delete(id));
  }

  // Walk upwards: one child implies the parent; multiple children require all
  // direct children before the parent is considered selected.
  let parentId = Number(departmentById.value.get(departmentId)?.parentId) || 0;
  while (parentId > 0) {
    const children = directChildren(parentId);
    const allChildrenSelected = children.length > 0 && children.every(child => selected.has(child.id));
    if (children.length === 1 || allChildrenSelected) selected.add(parentId);
    else selected.delete(parentId);
    parentId = Number(departmentById.value.get(parentId)?.parentId) || 0;
  }
  selectedDepartmentIds.value = [...selected];
}

async function edit(k: KnowledgeBase) {
  editingBase.value = k;
  createForm.name = k.name || '';
  createForm.description = k.description || '';
  createForm.category = k.category || '';
  createForm.visibility = k.visibility || 'DEPT';
  await loadDepartmentSelection(k);
  createFormRef.value?.clearValidate?.();
  createDialogVisible.value = true;
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
.knowledge-base-page {
  --kb-navy: #102752;
  --kb-blue: #4e7dff;
  --kb-cyan: #23d5e8;
  position: relative;
}

.kb-hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  margin-bottom: 20px;
  padding: 24px 28px 18px;
  border: 1px solid #244b89;
  border-radius: 16px;
  color: #fff;
  background: var(--kb-navy);
  box-shadow: 0 16px 32px rgba(16, 39, 82, .18);
}

.kb-hero::before {
  position: absolute;
  z-index: -1;
  top: -100px;
  right: -20px;
  width: 390px;
  height: 260px;
  border: 1px solid rgba(35, 213, 232, .22);
  border-radius: 50%;
  box-shadow: 0 0 0 26px rgba(35, 213, 232, .04), 0 0 0 52px rgba(35, 213, 232, .025);
  content: '';
  transform: rotate(-14deg);
}

.kb-hero-grid {
  position: absolute;
  z-index: -1;
  inset: 0;
  opacity: .16;
  background-image: linear-gradient(rgba(157, 190, 255, .16) 1px, transparent 1px), linear-gradient(90deg, rgba(157, 190, 255, .16) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(90deg, #000 0%, transparent 82%);
  pointer-events: none;
}

.kb-page-head {
  position: relative;
  z-index: 1;
  align-items: center;
  margin: 0;
}

.kb-head-copy .eyebrow {
  margin: 0 0 8px;
  color: #79dce7;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .13em;
}

.kb-head-copy h1 {
  margin: 0 0 6px;
  color: #fff;
  font-size: 27px;
  letter-spacing: .01em;
}

.kb-head-copy p:last-child {
  margin: 0;
  color: #b9c8e6;
  font-size: 13px;
}

.kb-head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.kb-head-actions :deep(.el-button--primary) {
  border-color: #fff;
  color: #173c83;
  background: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, .16);
}

.kb-head-actions :deep(.el-button--primary:hover) {
  border-color: #d9f8ff;
  color: #12336f;
  background: #d9f8ff;
}

.kb-live-indicator {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #b9e8ec;
  font-size: 11px;
  white-space: nowrap;
}

.kb-live-indicator i,
.kb-filter-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--kb-cyan);
  box-shadow: 0 0 0 4px rgba(35, 213, 232, .14), 0 0 10px rgba(35, 213, 232, .65);
}

.kb-hero-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: 24px;
}

.kb-hero-stat {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: baseline;
  column-gap: 10px;
  padding: 13px 22px 0 0;
  border-top: 1px solid rgba(184, 210, 255, .22);
}

.kb-hero-stat + .kb-hero-stat {
  padding-left: 22px;
  border-left: 1px solid rgba(184, 210, 255, .16);
}

.kb-hero-stat span,
.kb-hero-stat small {
  color: #a9bce1;
  font-size: 11px;
}

.kb-hero-stat strong {
  color: #fff;
  font-size: 23px;
  font-weight: 700;
  line-height: 1;
}

.kb-hero-stat small {
  grid-column: 1 / -1;
  margin-top: 4px;
  color: #7890ba;
}

.kb-filter-bar {
  min-height: 62px;
  margin-bottom: 18px;
  padding: 10px 14px;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, .82);
  box-shadow: var(--card-shadow);
  backdrop-filter: blur(10px);
}

.kb-filter-bar :deep(.el-input__wrapper),
.kb-filter-bar :deep(.el-select__wrapper) {
  background: #f6f8fc;
  box-shadow: 0 0 0 1px #e0e6f1 inset;
}

.kb-filter-bar :deep(.el-input__wrapper:focus-within),
.kb-filter-bar :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--kb-blue) inset, 0 0 0 3px rgba(78, 125, 255, .1);
}

.kb-filter-bar .filter-count {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-weight: 600;
}

.kb-filter-bar .kb-filter-dot {
  width: 6px;
  height: 6px;
  background: var(--kb-blue);
  box-shadow: 0 0 0 4px rgba(78, 125, 255, .12);
}

.knowledge-base-page .kb-cards {
  gap: 18px;
}

.knowledge-base-page .kb-card {
  position: relative;
  overflow: hidden;
  min-height: 232px;
  padding: 21px 20px 18px;
  border-color: #dce4f0;
  border-radius: 13px;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 6px 22px rgba(31, 62, 113, .07);
}

.kb-card-accent {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: #4e7dff;
}

.kb-card:nth-child(3n + 2) .kb-card-accent { background: #11aeb9; }
.kb-card:nth-child(3n) .kb-card-accent { background: #8056d9; }

.knowledge-base-page .kb-card:hover {
  border-color: #9cb8ff;
  box-shadow: 0 14px 28px rgba(39, 85, 177, .14);
  transform: translateY(-4px);
}

.knowledge-base-page .large-kb-icon {
  border: 1px solid #d9e4ff;
  border-radius: 11px;
  color: #3969de;
  background: #eff4ff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: .02em;
}

.kb-card:nth-child(3n + 2) .large-kb-icon { border-color: #c9eff1; color: #0a8d97; background: #effbfc; }
.kb-card:nth-child(3n) .large-kb-icon { border-color: #e4d8ff; color: #7048c9; background: #f6f1ff; }

.knowledge-base-page .kb-card h3 {
  margin-top: 17px;
  color: var(--text-strong);
  font-size: 16px;
  letter-spacing: .01em;
}

.knowledge-base-page .kb-card p {
  min-height: 38px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.65;
}

.knowledge-base-page .kb-meta {
  margin: 16px 0 14px;
  padding: 12px 0;
  border-color: #edf0f5;
  color: #8a98ae;
  font-size: 11px;
}

.knowledge-base-page .kb-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.knowledge-base-page .kb-meta .el-icon { color: #7092db; }
.knowledge-base-page .kb-foot a { color: #3b6ee8; font-size: 11px; font-weight: 600; }
.knowledge-base-page .kb-foot a:hover { color: #1d5cff; }
.knowledge-base-page .more-btn:hover { color: #3b6ee8; background: #edf3ff; }

:global(html.dark) .kb-filter-bar { background: rgba(24, 35, 56, .86); }
:global(html.dark) .knowledge-base-page .kb-card { background: rgba(24, 35, 56, .96); border-color: #314057; }
:global(html.dark) .knowledge-base-page .kb-meta { border-color: #314057; }

@media (max-width: 760px) {
  .kb-hero { padding: 20px 18px 16px; border-radius: 13px; }
  .kb-page-head { align-items: flex-start; gap: 16px; }
  .kb-head-actions { width: 100%; justify-content: space-between; }
  .kb-head-copy h1 { font-size: 23px; }
  .kb-hero-stats { margin-top: 20px; }
  .kb-hero-stat { display: block; padding: 11px 10px 0 0; }
  .kb-hero-stat + .kb-hero-stat { padding-left: 10px; }
  .kb-hero-stat strong { display: block; margin: 5px 0 0; font-size: 20px; }
  .kb-hero-stat small { display: block; font-size: 10px; }
  .kb-filter-bar { padding: 10px; }
}

@media (prefers-reduced-motion: reduce) {
  .knowledge-base-page .kb-card { transition: none; }
  .knowledge-base-page .kb-card:hover { transform: none; }
}

.department-access-intro {
  padding: 12px 14px;
  background: var(--soft-green);
  border-radius: 6px;
  color: var(--ink)
}
.department-access-section {
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
.department-access-intro b { font-size: 13px }
.department-access-intro p { margin: 6px 0 0; color: var(--muted); font-size: 12px; line-height: 1.6 }
.department-access-list {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  margin-top: 16px;
  padding: 0 2px;
}
.department-access-list :deep(.el-checkbox) {
  --department-indent: calc(var(--department-level, 0) * 24px);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 36px;
  margin-right: 0;
  padding: 6px 10px 6px var(--department-indent);
  border-radius: 6px;
  transition: background-color .18s ease;
}
.department-access-list :deep(.el-checkbox:hover) { background: var(--soft-blue); }
.department-access-list :deep(.el-checkbox__label) {
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: normal;
  line-height: 1.45;
}
.department-access-list :deep(.el-checkbox.is-disabled:hover) { background: transparent; }
</style>
