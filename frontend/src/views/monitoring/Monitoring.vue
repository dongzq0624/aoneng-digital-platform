<template>
  <div class="monitoring-page">
    <div class="page-head compact monitoring-head">
      <div>
        <p class="eyebrow">运行观测 / RAG质量</p>
        <h1>知识库质量监控仪表盘</h1>
        <p>统一查看文档索引、检索召回、回答质量、系统性能与用户反馈。</p>
      </div>
      <div class="head-actions">
        <span class="update-time">数据更新：{{ updatedAt }}</span>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
      </div>
    </div>

    <section class="filter-bar monitoring-filter" aria-label="监控筛选">
      <el-date-picker v-model="range" style="width: 300px" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss[Z]"
                      start-placeholder="开始时间" end-placeholder="结束时间" :shortcuts="dateShortcuts"/>
      <el-button @click="reset">重置</el-button>
      <el-button type="primary" @click="load">应用筛选</el-button>
    </section>

    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="monitoring-error"/>

    <section class="kpi-grid" aria-label="关键指标">
      <div v-for="item in kpis" :key="item.label" class="kpi-card" :class="item.tone">
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">{{ item.value }}</div>
        <div class="kpi-detail">{{ item.detail }}</div>
      </div>
    </section>

    <section v-if="alerts.length" class="alert-stack" aria-label="活跃告警">
      <div v-for="alert in alerts" :key="`${alert.title}-${alert.count}`" class="alert-item"
           :class="`alert-${alert.severity || 'info'}`">
        <el-icon>
          <WarningFilled/>
        </el-icon>
        <div><strong>{{ alert.title }}</strong><span v-if="alert.count">涉及 {{ alert.count }} 项</span></div>
      </div>
    </section>

    <nav class="monitor-tabs" aria-label="监控维度">
      <button v-for="tab in tabs" :key="tab.key" type="button" class="monitor-tab"
              :class="{active: activeTab === tab.key}" @click="activeTab = tab.key">{{ tab.label }}
      </button>
    </nav>

    <section v-show="activeTab === 'document'" class="tab-content">
      <div class="chart-row">
        <ChartCard title="文档索引状态分布" chart-id="doc-status" :option="docStatusOption"
                   :empty="!documentStatus.length" :set-ref="setChartRef"/>
        <ChartCard title="父块 Token 分布" subtitle="合理区间：1200–2000" chart-id="token-distribution"
                   :option="tokenOption" :empty="!tokenDistribution.length" :set-ref="setChartRef"/>
      </div>
      <DataTable title="失败与降级文档" :rows="failedDocuments" :columns="['docId','fileName','status','reason']"
                 empty-text="暂无失败或降级文档">
        <template #default="{row}">
          <td>{{ row.docId }}</td>
          <td class="text-ellipsis" :title="row.fileName">{{ row.fileName }}</td>
          <td>
            <el-tag :type="row.chunkStatus === 'FAILED' || row.parseStatus === 'FAILED' ? 'danger' : 'warning'">
              {{ documentState(row) }}
            </el-tag>
          </td>
          <td class="text-ellipsis" :title="row.reason">{{ row.reason || '未提供原因' }}</td>
        </template>
      </DataTable>
    </section>

    <section v-show="activeTab === 'retrieval'" class="tab-content">
      <div class="chart-row">
        <ChartCard title="离线评测 Hit@K 趋势" chart-id="hit-trend" :option="hitOption"
                   :empty="!retrieval.hitTrend?.length" :set-ref="setChartRef"/>
        <ChartCard title="首位命中相似度分布" chart-id="similarity" :option="similarityOption"
                   :empty="!retrieval.similarity?.length" :set-ref="setChartRef"/>
      </div>
      <div class="chart-row">
        <ChartCard title="混合检索命中构成" chart-id="hybrid" :option="hybridOption" :empty="!retrieval.hybrid?.length"
                   :set-ref="setChartRef"/>
        <ChartCard title="高频召回文档" chart-id="hot-documents" :option="hotDocOption"
                   :empty="!retrieval.hotDocuments?.length" :set-ref="setChartRef"/>
      </div>
      <div v-if="retrieval.lowSimilarityQueries?.length" class="notice notice-warning">
        <strong>低相似度问题</strong><span>{{ retrieval.lowSimilarityQueries.join('、') }}</span></div>
    </section>

    <section v-show="activeTab === 'generation'" class="tab-content">
      <div class="chart-row">
        <ChartCard title="回答引用情况" chart-id="citation" :option="citationOption"
                   :empty="!generation.citation?.length" :set-ref="setChartRef"/>
        <ChartCard title="提示词 Token 分布" subtitle="超过 6500 视为高风险" chart-id="prompt-tokens"
                   :option="promptOption" :empty="!generation.promptTokens?.length" :set-ref="setChartRef"/>
      </div>
      <div v-if="generation.riskSamples?.length" class="table-wrap">
        <div class="section-title">待人工复核样本</div>
        <table>
          <thead>
          <tr>
            <th>请求编号</th>
            <th>用户问题</th>
            <th>风险类型</th>
            <th>召回块数</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="row in generation.riskSamples" :key="row.requestId">
            <td>{{ row.requestId }}</td>
            <td>{{ row.question }}</td>
            <td>
              <el-tag type="danger">{{ row.riskType }}</el-tag>
            </td>
            <td>{{ row.retrievedCount ?? 0 }}</td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-inline">暂无高风险回答样本</div>
    </section>

    <section v-show="activeTab === 'performance'" class="tab-content">
      <ChartCard title="各链路阶段 P50 / P95 耗时（ms）" chart-id="performance"
                 :option="performanceOption" :empty="!performanceStages.length" :set-ref="setChartRef" :large="true"/>
      <div class="performance-grid performance-kpis">
        <div v-for="item in performanceKpis" :key="item.label" class="perf-card" :class="item.tone">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
      <div v-if="performanceNotice" class="performance-notice">
        <div class="performance-notice-title">性能瓶颈提示</div>
        <div>{{ performanceNotice }}</div>
      </div>
    </section>

    <section v-show="activeTab === 'feedback'" class="tab-content">
      <div class="chart-row">
        <ChartCard title="用户反馈分布" chart-id="feedback" :option="feedbackOption"
                   :empty="!feedback.distribution?.length" :set-ref="setChartRef"/>
        <ChartCard title="点踩原因分布" chart-id="feedback-reasons" :option="feedbackReasonOption"
                   :empty="!feedback.reasons?.length" :set-ref="setChartRef"/>
      </div>
      <div class="chart-row">
        <ChartCard title="反馈趋势" subtitle="按日统计" chart-id="feedback-trend" :option="feedbackTrendOption"
                   :empty="!feedback.trend?.length" :set-ref="setChartRef"/>
        <ChartCard title="各知识库满意度" chart-id="kb-satisfaction" :option="kbSatisfactionOption"
                   :empty="!feedback.knowledgeBases?.length" :set-ref="setChartRef"/>
      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {
  Refresh,
  WarningFilled
} from '@element-plus/icons-vue'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TooltipComponent, GraphicComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import * as echarts from 'echarts/core'
import {ElMessage} from 'element-plus'
import {ApiError, monitoringApi} from '../../api'
import {default as ChartCard} from '../../components/monitoring/MonitoringChartCard.vue'
import {default as DataTable} from '../../components/monitoring/MonitoringDataTable.vue'

type AnyMap = Record<string, any>
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, GraphicComponent, CanvasRenderer])
const loading = ref(false);
const error = ref('');
const updatedAt = ref('-');
const range = ref<string[]>([]);
const activeTab = ref('document');
const dashboard = ref<AnyMap>({});
const dateShortcuts = [
  {text: '近1天', value: () => dateRange(1)},
  {text: '近7天', value: () => dateRange(7)},
  {text: '近30天', value: () => dateRange(30)},
  {text: '近90天', value: () => dateRange(90)}
]
const tabs = [{key: 'document', label: '文档索引质量'}, {key: 'retrieval', label: '检索召回质量'}, {
  key: 'generation',
  label: '生成回答质量'
}, {key: 'performance', label: '系统性能'}, {key: 'feedback', label: '用户反馈'}]
const chartRefs = new Map<string, HTMLElement>();
const charts = new Map<string, echarts.ECharts>()
const overview = computed(() => dashboard.value.overview || {});
const quality = computed(() => overview.value.quality || {});
const summary = computed(() => overview.value.summary || {});
const stages = computed(() => overview.value.stages || [])
const performanceStages = computed(() => {
  const order = ['document.parse', 'document.chunk', 'embedding.dense', 'retrieval.hybrid_rrf', 'llm.rerank', 'llm.chat']
  return order.map(operation => stages.value.find((stage: AnyMap) => stage.operation === operation)).filter(Boolean) as AnyMap[]
})
const performance = computed(() => overview.value.performance || {})
const documentQuality = computed(() => dashboard.value.documentQuality || {});
const documentStatus = computed(() => documentQuality.value.status || []);
const tokenDistribution = computed(() => documentQuality.value.tokens || []);
const failedDocuments = computed(() => documentQuality.value.failed || [])
const retrieval = computed(() => dashboard.value.retrievalQuality || {});
const generation = computed(() => dashboard.value.generationQuality || {});
const feedback = computed(() => dashboard.value.feedback || {});
const alerts = computed(() => dashboard.value.alerts || [])
const percent = (v: unknown) => {
  const n = Number(v);
  return Number.isFinite(n) ? `${(n <= 1 ? n * 100 : n).toFixed(1)}%` : '0.0%'
};
const errorRate = (v: unknown) => {
  const n = Number(v);
  if (!Number.isFinite(n)) return '0%';
  const digits = n > 0 && n < 0.1 ? 2 : 1;
  return `${n.toFixed(digits).replace(/\.0+$/, '')}%`;
};
const score = (v: unknown) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.max(0, Math.min(100, n <= 1 ? n * 100 : n)) : 0
};
const ms = (v: unknown) => {
  const n = Number(v);
  return Number.isFinite(n) ? `${n.toFixed(1)} 毫秒` : '-'
};
const stageLabel = (v: string) => ({
  'document.parse': '文档解析',
  'document.chunk': '文本分块',
  'embedding.dense': '语义向量化',
  'embedding.sparse_bm25': '关键词向量化',
  'vector.upsert': '向量写入',
  'retrieval.hybrid_rrf': '混合检索与融合排序',
  'llm.rerank': '模型重排序',
  'llm.chat': '回答生成',
  'task.schedule': '任务调度',
  'task.recovery': '任务恢复',
  'object.scan': '对象变更扫描'
} as AnyMap)[v] || v || '-'
const stageChartLabel = (v: string) => ({
  'document.parse': 'Docling解析',
  'document.chunk': '分块处理',
  'embedding.dense': 'Embedding',
  'retrieval.hybrid_rrf': 'Milvus检索',
  'llm.rerank': 'Rerank重排',
  'llm.chat': 'LLM生成'
} as AnyMap)[v] || stageLabel(v)
const kpis = computed(() => [{
  label: '文档总数',
  value: documentQuality.value.total ?? '-',
  detail: `索引成功 ${statusCount('INDEXED')} · 失败 ${statusCount('FAILED')}`,
  tone: 'blue'
}, {
  label: '解析成功率',
  value: percent(documentQuality.value.parseSuccessRate),
  detail: `降级 ${documentQuality.value.fallbackCount ?? 0} 份`,
  tone: 'green'
}, {
  label: '离线命中率',
  value: percent(quality.value.hitAt5),
  detail: '当前评测集 Hit@5',
  tone: 'cyan'
}, {
  label: '回答引用率',
  value: percent(generation.value.citationRate),
  detail: `无引用 ${percent(1 - Number(generation.value.citationRate || 0))}`,
  tone: 'purple'
}, {
  label: '用户点赞率',
  value: percent(feedback.value.likeRate),
  detail: `点踩 ${percent(feedback.value.dislikeRate)}`,
  tone: 'orange'
}, {label: '活跃告警', value: alerts.value.length, detail: '需关注的异常项', tone: 'red'}])
const performanceKpis = computed(() => [
  {label: 'Docling 错误率', value: errorRate(performance.value.doclingErrorRate), tone: Number(performance.value.doclingErrorRate || 0) > 1 ? 'warning' : 'success'},
  {label: 'Embedding 错误率', value: errorRate(performance.value.embeddingErrorRate), tone: Number(performance.value.embeddingErrorRate || 0) > 1 ? 'warning' : 'success'},
  {label: 'Milvus 错误率', value: errorRate(performance.value.milvusErrorRate), tone: Number(performance.value.milvusErrorRate || 0) > 1 ? 'warning' : 'success'},
  {label: 'LLM 错误率', value: errorRate(performance.value.llmErrorRate), tone: Number(performance.value.llmErrorRate || 0) > 1 ? 'warning' : 'success'},
  {label: 'Rerank 错误率', value: errorRate(performance.value.rerankErrorRate), tone: Number(performance.value.rerankErrorRate || 0) > 1 ? 'warning' : 'success'},
  {label: '今日问答 QPS', value: performance.value.todayQps ?? 0, tone: 'success'}
])
const performanceNotice = computed(() => {
  const docling = stages.value.find((stage: AnyMap) => stage.operation === 'document.parse')
  if (docling && Number(docling.p95_ms) >= 3000) {
    return `Docling 解析 P95 达 ${Math.round(Number(docling.p95_ms))}ms，扫描件 PDF 占比上升导致耗时增加，建议：扫描文档量大时考虑 GPU 部署 docling-serve，或增加解析服务副本数。`
  }
  return ''
})
const statusCount = (status: string) => documentStatus.value.find((x: AnyMap) => x.status === status)?.count ?? 0;
const documentState = (row: AnyMap) => row.chunkStatus === 'FAILED' || row.parseStatus === 'FAILED' ? '索引失败' : row.parseStatus === 'SUCCESS' ? '解析降级' : '处理中'
const params = () => ({
  ...(range.value?.length === 2 ? {
    from: range.value[0],
    to: range.value[1]
  } : {})
})
function dateRange(days: number): [Date, Date] {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - (days - 1))
  start.setHours(0, 0, 0, 0)
  return [start, end]
}
const axis = (labels: any[], data: any[], color = '#9bbbf4') => ({
  tooltip: {trigger: 'axis', confine: true},
  grid: {left: 42, right: 20, top: 28, bottom: 34, containLabel: true},
  xAxis: {type: 'category', data: labels, axisLabel: {fontSize: 11, color: '#64748b'}},
  yAxis: {type: 'value', axisLabel: {fontSize: 11, color: '#64748b'}},
  series: [{type: 'bar', data, itemStyle: {color, borderRadius: [4, 4, 0, 0]}, barWidth: '48%'}]
})
const pie = (data: any[]) => ({
  tooltip: {trigger: 'item', confine: true},
  legend: {bottom: 0, textStyle: {fontSize: 11}},
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['50%', '43%'],
    data,
    label: {fontSize: 11, formatter: '{b}\n{d}%'}
  }]
})
const docStatusOption = computed(() => pie(documentStatus.value.map((x: AnyMap) => ({
  value: x.count,
  name: x.status === 'INDEXED' ? '索引成功' : x.status === 'FAILED' ? '索引失败' : x.status === 'INDEXING' ? '索引中' : x.status
}))));
const tokenOption = computed(() => axis(tokenDistribution.value.map((x: AnyMap) => x.bucket), tokenDistribution.value.map((x: AnyMap) => x.count)));
const hitOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  legend: {top: 0},
  grid: {left: 42, right: 20, top: 34, bottom: 30, containLabel: true},
  xAxis: {type: 'category', data: (retrieval.value.hitTrend || []).map((x: AnyMap) => x.period)},
  yAxis: {type: 'value', max: 100},
  series: [{
    name: '命中率',
    type: 'line',
    smooth: true,
    data: (retrieval.value.hitTrend || []).map((x: AnyMap) => x.hitAt5),
    itemStyle: {color: '#4967ed'},
    areaStyle: {color: 'rgba(73,103,237,.10)'}
  }]
}));
const similarityOption = computed(() => axis((retrieval.value.similarity || []).map((x: AnyMap) => x.bucket), (retrieval.value.similarity || []).map((x: AnyMap) => x.count), '#8bc8ea'));
const hybridOption = computed(() => pie((retrieval.value.hybrid || []).map((x: AnyMap) => ({
  value: x.count,
  name: x.source === 'dense' ? '仅语义命中' : x.source === 'sparse' ? '仅关键词命中' : x.source === 'both' ? '共同命中' : x.source
}))));
const hotDocOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  grid: {left: 100, right: 38, top: 10, bottom: 22, containLabel: true},
  xAxis: {type: 'value'},
  yAxis: {
    type: 'category',
    data: (retrieval.value.hotDocuments || []).slice().reverse().map((x: AnyMap) => x.fileName)
  },
  series: [{
    type: 'bar',
    data: (retrieval.value.hotDocuments || []).slice().reverse().map((x: AnyMap) => x.count),
    itemStyle: {color: '#9bbbf4', borderRadius: [0, 4, 4, 0]},
    label: {show: true, position: 'right'}
  }]
}));
const citationOption = computed(() => pie((generation.value.citation || []).map((x: AnyMap) => ({
  value: x.count,
  name: x.status
}))));
const promptOption = computed(() => axis((generation.value.promptTokens || []).map((x: AnyMap) => x.bucket), (generation.value.promptTokens || []).map((x: AnyMap) => x.count), '#c9a7e8'));
const performanceOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  legend: {top: 0},
  grid: {left: 70, right: 20, top: 34, bottom: 34, containLabel: true},
  xAxis: {
    type: 'category',
    data: performanceStages.value.map((x: AnyMap) => stageChartLabel(x.operation)),
    axisLabel: {interval: 0, fontSize: 12, color: '#475569'}
  },
  yAxis: {type: 'value', name: 'ms', axisLabel: {color: '#64748b'}},
  series: [{
    name: 'P50',
    type: 'bar',
    data: performanceStages.value.map((x: AnyMap) => x.p50_ms ?? x.avg_ms),
    itemStyle: {color: '#8fb1ef', borderRadius: [6, 6, 0, 0]}
  }, {name: 'P95', type: 'bar', data: performanceStages.value.map((x: AnyMap) => x.p95_ms), itemStyle: {color: '#ef666b', borderRadius: [6, 6, 0, 0]}}]
}));
const feedbackOption = computed(() => pie((feedback.value.distribution || []).map((x: AnyMap) => ({
  value: x.count,
  name: x.status
}))));
const feedbackReasonOption = computed(() => axis((feedback.value.reasons || []).map((x: AnyMap) => x.reason), (feedback.value.reasons || []).map((x: AnyMap) => x.count), '#f4b393'));
const feedbackTrendOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  legend: {top: 0},
  grid: {left: 42, right: 20, top: 34, bottom: 30, containLabel: true},
  xAxis: {type: 'category', data: (feedback.value.trend || []).map((x: AnyMap) => x.day)},
  yAxis: {type: 'value'},
  series: [{
    name: '点赞',
    type: 'line',
    smooth: true,
    data: (feedback.value.trend || []).map((x: AnyMap) => x.likes),
    itemStyle: {color: '#52c41a'}
  }, {
    name: '点踩',
    type: 'line',
    smooth: true,
    data: (feedback.value.trend || []).map((x: AnyMap) => x.dislikes),
    itemStyle: {color: '#ea6668'}
  }]
}));
const kbSatisfactionOption = computed(() => axis((feedback.value.knowledgeBases || []).map((x: AnyMap) => x.name), (feedback.value.knowledgeBases || []).map((x: AnyMap) => x.rate), '#9bbbf4'))

function setChartRef(id: string, el: unknown) {
  if (el instanceof HTMLElement) chartRefs.set(id, el); else if (el === null) chartRefs.delete(id)
}

function emptyOption(option: any) {
  return {
    ...option,
    graphic: {type: 'text', left: 'center', top: 'middle', style: {text: '暂无数据', fill: '#94a3b8', fontSize: 13}}
  }
}

function renderCharts() {
  nextTick(() => {
    const options: AnyMap = {
      'doc-status': docStatusOption.value,
      'token-distribution': tokenOption.value,
      'hit-trend': hitOption.value,
      similarity: similarityOption.value,
      hybrid: hybridOption.value,
      'hot-documents': hotDocOption.value,
      citation: citationOption.value,
      'prompt-tokens': promptOption.value,
      performance: performanceOption.value,
      feedback: feedbackOption.value,
      'feedback-reasons': feedbackReasonOption.value,
      'feedback-trend': feedbackTrendOption.value,
      'kb-satisfaction': kbSatisfactionOption.value
    };
    chartRefs.forEach((el, id) => {
      let chart = charts.get(id);
      if (!chart) {
        chart = echarts.init(el);
        charts.set(id, chart)
      }
      const option = options[id] || {};
      const hasData = (option.series || []).some((series: AnyMap) => Array.isArray(series.data) && series.data.length > 0);
      chart.setOption(hasData ? option : emptyOption(option), true);
      chart.resize()
    })
  })
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const dash = await monitoringApi.dashboard(params());
    dashboard.value = dash.data || {};
    updatedAt.value = dashboard.value.updatedAt ? new Date(dashboard.value.updatedAt).toLocaleString('zh-CN') : '-';
    renderCharts()
  } catch (e) {
    error.value = e instanceof ApiError && e.status === 403 ? '当前账号无权查看监控数据' : e instanceof Error ? e.message : '监控数据加载失败';
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

function reset() {
  range.value = [];
  void load()
};

watch([activeTab, documentStatus, tokenDistribution, retrieval, generation, feedback, stages], renderCharts, {deep: true});
onMounted(() => {
  load();
  window.addEventListener('resize', renderCharts)
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', renderCharts);
  charts.forEach(c => c.dispose())
})

</script>

<style scoped>
.monitoring-page {
  --dash-border: #e4e3dd;
  --dash-muted: #6b7280;
  --dash-bg: #f4f3ee;
  background: #f4f3ee;
  margin: -24px -28px -48px;
  padding: 24px 28px 48px;
  min-height: calc(100vh - 110px)
}

.monitoring-head {
  align-items: flex-end
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 14px
}

.update-time {
  font-size: 12px;
  color: var(--dash-muted);
  white-space: nowrap
}

.monitoring-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid var(--dash-border);
  border-radius: 10px
}

.monitoring-filter :deep(.el-date-editor) {
  width: min(430px, 100%);
  min-width: 330px
}

.monitoring-error {
  margin-bottom: 14px
}

.kpi-grid {
  display: grid;
  grid-template-columns:repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px
}

.kpi-card {
  position: relative;
  min-height: 114px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--dash-border);
  border-radius: 10px;
  overflow: hidden
}

.kpi-card:before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: #9bbbf4
}

.kpi-card.green:before {
  background: #52c41a
}

.kpi-card.cyan:before {
  background: #8bc8ea
}

.kpi-card.purple:before {
  background: #c9a7e8
}

.kpi-card.orange:before {
  background: #f4b393
}

.kpi-card.red:before {
  background: #ea6668
}

.kpi-label {
  font-size: 12px;
  color: var(--dash-muted)
}

.kpi-value {
  margin-top: 7px;
  color: #1a1b1c;
  font: 700 24px/1.1 Manrope, sans-serif;
  font-variant-numeric: tabular-nums
}

.kpi-detail {
  margin-top: 7px;
  color: var(--dash-muted);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis
}

.alert-stack {
  display: grid;
  gap: 8px;
  margin-bottom: 16px
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 14px;
  border-radius: 8px;
  border-left: 3px solid #9bbbf4;
  background: rgba(155, 187, 244, .08);
  color: #4a6fa5;
  font-size: 12px
}

.alert-item :deep(.el-icon) {
  margin-top: 1px
}

.alert-item div {
  display: flex;
  gap: 10px;
  flex-wrap: wrap
}

.alert-item strong {
  font-weight: 650
}

.alert-item span {
  color: var(--dash-muted)
}

.alert-danger {
  border-left-color: #ea6668;
  background: rgba(234, 102, 104, .07);
  color: #c23e4e
}

.alert-warning {
  border-left-color: #faad14;
  background: rgba(250, 173, 20, .09);
  color: #a66a12
}

.monitor-tabs {
  display: flex;
  gap: 6px;
  border-bottom: 2px solid var(--dash-border);
  margin-bottom: 14px;
  overflow-x: auto
}

.monitor-tab {
  border: 0;
  background: transparent;
  padding: 10px 17px;
  margin-bottom: -2px;
  border-bottom: 2px solid transparent;
  color: var(--dash-muted);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer
}

.monitor-tab:hover, .monitor-tab.active {
  color: #4a6fa5
}

.monitor-tab.active {
  border-bottom-color: #9bbbf4;
  font-weight: 650
}

.tab-content {
  min-height: 100px
}

.chart-row {
  display: grid;
  grid-template-columns:repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px
}

.chart-box, .table-wrap {
  min-width: 0;
  background: #fff;
  border: 1px solid var(--dash-border);
  border-radius: 10px;
  padding: 14px
}

.chart-box.large {
  margin-bottom: 14px
}

.section-title {
  font-size: 13px;
  font-weight: 650;
  color: #1a1b1c;
  margin-bottom: 8px
}

.section-title small {
  margin-left: 8px;
  color: var(--dash-muted);
  font-size: 11px;
  font-weight: 400
}

.chart-container {
  height: 248px;
  width: 100%
}

.chart-box.large .chart-container {
  height: 290px
}

.notice {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.7
}

.notice-warning {
  border-left: 3px solid #faad14;
  background: rgba(250, 173, 20, .09);
  color: #8c620f
}

.notice span {
  color: var(--dash-muted)
}

.table-wrap {
  overflow-x: auto;
  margin-bottom: 14px
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px
}

th {
  padding: 8px 10px;
  text-align: left;
  background: #f4f3ee;
  color: var(--dash-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--dash-border);
  white-space: nowrap
}

td {
  padding: 9px 10px;
  border-bottom: 1px solid #f0f0ed;
  color: #1a1b1c
}

.text-ellipsis {
  max-width: 270px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.empty-inline {
  padding: 34px 12px;
  text-align: center;
  color: #9aa1ad;
  font-size: 12px
}

.performance-grid {
  display: grid;
  grid-template-columns:repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px
}

.perf-card {
  min-height: 90px;
  padding: 14px 18px;
  border-radius: 8px;
  background: rgba(82, 196, 26, .10);
  display: flex;
  flex-direction: column;
  justify-content: center
}

.perf-card.warning {
  background: rgba(250, 173, 20, .1)
}

.perf-card span {
  display: block;
  color: var(--dash-muted);
  font-size: 13px
}

.perf-card strong {
  display: block;
  margin-top: 4px;
  color: #52c41a;
  font: 700 28px/1.1 Manrope, sans-serif;
  font-variant-numeric: tabular-nums
}

.perf-card.warning strong {
  color: #b8860b
}

.performance-notice {
  min-height: 92px;
  padding: 14px 24px;
  border-left: 3px solid #8fb1ef;
  border-radius: 8px;
  background: #eef1f2;
  color: #10233d;
  font-size: 13px;
  line-height: 1.7
}

.performance-notice-title {
  margin-bottom: 3px;
  color: #3e6ea8;
  font-weight: 650
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns:repeat(3, minmax(0, 1fr))
  }

  .performance-grid {
    grid-template-columns:repeat(3, minmax(0, 1fr))
  }
}

@media (max-width: 800px) {
  .monitoring-page {
    margin: -14px -14px -32px;
    padding: 18px 14px 32px
  }

  .monitoring-head {
    align-items: flex-start
  }

  .head-actions {
    width: 100%;
    justify-content: space-between
  }

  .monitoring-filter :deep(.el-date-editor) {
    min-width: 100%;
    width: 100%
  }

  .kpi-grid {
    grid-template-columns:repeat(2, minmax(0, 1fr));
    gap: 8px
  }

  .chart-row {
    grid-template-columns:1fr
  }

  .performance-grid {
    grid-template-columns:repeat(2, minmax(0, 1fr))
  }

}

@media (max-width: 480px) {
  .head-actions {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px
  }

  .kpi-card {
    min-height: 104px;
    padding: 12px
  }

  .kpi-value {
    font-size: 20px
  }

  .chart-container {
    height: 220px
  }

}
</style>
