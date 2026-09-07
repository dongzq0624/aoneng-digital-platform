<template>
  <div>
    <div class="page-head">
      <div><p class="eyebrow">{{ todayLabel }}</p>
        <h1>早上好，{{ userName }}</h1>
        <p>今天也保持专注，完成重要的事。</p></div>
      <div class="date-card"><b>{{ todayLabel }}</b><span>企业数字化平台</span></div>
    </div>
    <div class="hero">
      <div><span>知识驱动增长</span>
        <h2>让每一次提问<br/>都有可靠答案</h2>
        <p>企业知识库已连接 {{ stats.kb }} 个知识空间，随时为你提供支持。</p>
        <RouterLink to="/chat" class="hero-btn">开始智能问答 <el-icon><ArrowRight /></el-icon></RouterLink>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="energy-array"><i v-for="cell in 12" :key="cell"></i></div>
        <div class="hero-stat"><b>{{ stats.recallRate }}%</b><small>知识召回率</small></div>
      </div>
    </div>
    <div class="stat-grid">
      <div class="stat" v-for="item in statCards" :key="item.label"><span><el-icon><component :is="item.icon" /></el-icon></span>
        <div><small>{{ item.label }}</small><b>{{ item.value }}</b><em>{{ item.delta }}</em></div>
      </div>
    </div>
    <div class="dash-grid dashboard-knowledge-grid">
      <section class="panel">
        <div class="panel-title">
          <div><h3>最近使用的知识库</h3><small>快速进入你关注的内容</small></div>
          <RouterLink to="/kb">查看全部 →</RouterLink>
        </div>
        <div class="kb-list">
          <div class="kb-row" v-for="kb in recentKbs" :key="kb.id"><span class="kb-icon"><el-icon><component :is="categoryIcon(kb.category)" /></el-icon></span>
            <div><b>{{ kb.name }}</b><small>{{ kb.docs }} 篇文档 · {{ visibilityLabel(kb.visibility) }}</small></div>
            <strong>{{ kb.updated }}</strong></div>
        </div>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">import {onMounted, reactive, ref} from 'vue';
import {ArrowRight, ChatDotRound, Collection, Document, FolderOpened, Histogram, Reading} from '@element-plus/icons-vue';
import {ElMessage} from 'element-plus';
import {authApi, dashboardApi} from '../../api';
import {formatBeijingTime} from '../../utils/datetime';

const stats = reactive({kb: 0, recallRate: 0});
const statCards = reactive([{label: '知识库空间', value: '0', delta: '接口统计', icon: FolderOpened}, {
  label: '文档总量',
  value: '0',
  delta: '接口统计',
  icon: Collection
}, {label: '今日问答', value: '0', delta: '接口统计', icon: ChatDotRound}, {
  label: '知识召回率',
  value: '0%',
  delta: '接口统计',
  icon: Histogram
}]);
const recentKbs = ref<any[]>([]);
const userName = ref('同事');
const todayLabel = formatBeijingTime(new Date());
const visibilityLabel = (v?: string) => ({PRIVATE: '仅自己', DEPT: '允许部门', ORG: '全公司', PUBLIC: '公开'} as Record<string, string>)[v || ''] || v || '-';

onMounted(async () => {
  try {
    const [{data}, me] = await Promise.all([dashboardApi.summary(), authApi.me()]);
    stats.kb = data.knowledgeBaseCount;
    stats.recallRate = data.recallRate;
    userName.value = me.data.realName || me.data.username;
    statCards[0].value = String(data.knowledgeBaseCount);
    statCards[1].value = String(data.documentCount);
    statCards[2].value = String(data.todayQaCount);
    statCards[3].value = `${data.recallRate}%`;
    recentKbs.value = (data.recentKbs || []).map((item: any) => ({
      ...item,
      visibility: item.visibility,
      updated: formatBeijingTime(item.updatedAt || item.updatedat),
      docs: item.docs ?? item.doccount
    }));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作台数据加载失败')
  }
})

function categoryIcon(category?: string) {
  if (category?.includes('文档')) return Document;
  if (category?.includes('手册')) return Reading;
  return FolderOpened
}

</script>
<style scoped>
.dashboard-knowledge-grid {
  grid-template-columns: minmax(0, 1fr);
}
</style>
