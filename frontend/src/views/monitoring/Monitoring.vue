<template>
  <div class="ragas-page">
    <header class="ragas-head">
      <div>
        <p class="eyebrow">RAG QUALITY / EVALUATION</p>
        <h1>RAGAS 评估面板</h1>
        <p>基于已完成的 RAGAS 评估任务，持续观测回答与检索上下文质量。</p>
      </div>
      <div class="ragas-actions">
        <el-select v-model="windowDays" aria-label="评估时间范围" @change="load">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" class="ragas-error" type="error" :title="error" show-icon :closable="false" />

    <section class="ragas-summary" aria-label="RAGAS 评估概览">
      <div class="summary-item" :class="{danger: healthState === 'degraded'}">
        <span>通过率</span>
        <strong>{{ passRate }}</strong>
        <small>{{ evaluation.passed || 0 }} / {{ evaluation.completed || 0 }} 次运行</small>
      </div>
      <div class="summary-item">
        <span>最近运行</span>
        <strong class="summary-date">{{ lastRun }}</strong>
        <small>{{ evaluation.evaluated || 0 }} 个有效评估样本</small>
      </div>
      <div class="summary-item">
        <span>观察窗口</span>
        <strong>{{ windowDays }} 天</strong>
        <small>{{ formattedWindow }}</small>
      </div>
      <div class="summary-item" :class="healthState">
        <span>综合健康度</span>
        <strong>{{ healthLabel }}</strong>
        <small>基于 4 个维度阈值</small>
      </div>
    </section>

    <section class="ragas-metric-grid" aria-label="RAGAS 指标">
      <article v-for="metric in metricCards" :key="metric.key" class="ragas-metric-card" :class="metric.status">
        <div class="metric-topline">
          <div>
            <span class="metric-name">{{ metric.label }}</span>
            <el-tooltip :content="metric.description" placement="top" :show-after="220">
              <button type="button" class="metric-help" :aria-label="`${metric.label} 指标说明`">?</button>
            </el-tooltip>
          </div>
          <span class="metric-delta" :class="metric.delta === null ? '' : metric.delta >= 0 ? 'up' : 'down'">{{ formatDelta(metric.delta) }}</span>
        </div>
        <strong class="metric-score">{{ metric.score }}</strong>
        <span class="metric-threshold">阈值 ≥ {{ metric.threshold.toFixed(2) }} · 评分范围 0–1</span>
        <div class="metric-sparkline" aria-hidden="true">
          <svg viewBox="0 0 220 58" preserveAspectRatio="none">
            <defs>
              <linearGradient :id="`fill-${metric.key}`" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" :stop-color="metric.color" stop-opacity=".22" />
                <stop offset="100%" :stop-color="metric.color" stop-opacity=".02" />
              </linearGradient>
            </defs>
            <line x1="0" :y1="thresholdY(metric.threshold)" x2="220" :y2="thresholdY(metric.threshold)" stroke="#cbd5e1" stroke-dasharray="3 4" />
            <path v-if="metric.sparkline.length" :d="sparkArea(metric.sparkline)" :fill="`url(#fill-${metric.key})`" />
            <polyline v-if="metric.sparkline.length" :points="sparkPoints(metric.sparkline)" fill="none" :stroke="metric.color" stroke-width="2.2" vector-effect="non-scaling-stroke" />
            <circle v-if="metric.sparkline.length" :cx="lastPoint(metric.sparkline).x" :cy="lastPoint(metric.sparkline).y" r="3" :fill="metric.color" />
          </svg>
          <span v-if="!metric.sparkline.length">暂无趋势数据</span>
        </div>
        <div class="metric-stat-row">
          <span>MIN <b>{{ metric.min }}</b></span>
          <span>AVG <b>{{ metric.score }}</b></span>
          <span>MAX <b>{{ metric.max }}</b></span>
        </div>
      </article>
    </section>

    <section class="ragas-trend-card">
      <div class="trend-head">
        <div>
          <h2>四个维度趋势对比</h2>
          <p>按评估完成日聚合，展示真实 RAGAS 评分变化。</p>
        </div>
        <div class="trend-legend" aria-label="指标图例">
          <span v-for="metric in metricDefinitions" :key="metric.key"><i :style="{background: metric.color}"></i>{{ metric.label }}</span>
        </div>
      </div>
      <div ref="trendChart" class="ragas-trend-chart" aria-label="RAGAS 四维趋势图"></div>
      <div v-if="!evaluation.trend?.length" class="trend-empty">当前时间范围内暂无已完成的 RAGAS 评估结果</div>
    </section>

    <section class="metric-definitions" aria-label="指标口径">
      <article v-for="metric in metricDefinitions" :key="metric.key">
        <span class="definition-marker" :style="{background: metric.color}"></span>
        <div><b>{{ metric.label }}</b><p>{{ metric.formula }}</p></div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {Refresh} from '@element-plus/icons-vue'
import {LineChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import * as echarts from 'echarts/core'
import {ElMessage} from 'element-plus'
import {ApiError, monitoringApi, type RagasEvaluation, type RagasMetricSummary} from '../../api'
import {formatBeijingTime} from '../../utils/datetime'

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

type MetricKey = 'faithfulness' | 'answerRelevancy' | 'contextPrecision' | 'contextRecall'
type TrendPoint = {x: number; y: number}

const loading = ref(false)
const error = ref('')
const windowDays = ref(30)
const evaluation = ref<Partial<RagasEvaluation>>({metrics: {} as RagasEvaluation['metrics'], trend: []})
const trendChart = ref<HTMLElement>()
let chart: echarts.ECharts | undefined

const metricDefinitions: Array<{key: MetricKey; label: string; color: string; description: string; formula: string}> = [
  {
    key: 'faithfulness', label: 'Faithfulness', color: '#2563eb',
    description: '衡量回答中的声明是否均可由检索上下文支撑。',
    formula: '忠实度 = 被检索上下文支持的回答声明数 / 回答声明总数。'
  },
  {
    key: 'answerRelevancy', label: 'Answer Relevancy', color: '#7556d9',
    description: '衡量回答是否直接回应用户问题；兼容历史 answer_correctness 结果。',
    formula: '回答相关性 = 原问题与由回答反向生成问题之间的平均语义相似度。'
  },
  {
    key: 'contextPrecision', label: 'Context Precision', color: '#e4a11b',
    description: '衡量相关上下文是否排在检索结果前列。',
    formula: '上下文精确率 = Σ(Precision@k × 第 k 项相关性) / 相关上下文数量。'
  },
  {
    key: 'contextRecall', label: 'Context Recall', color: '#14a897',
    description: '衡量检索上下文对参考答案所需信息的覆盖程度。',
    formula: '上下文召回率 = 被检索上下文覆盖的参考答案声明数 / 参考答案声明总数。'
  },
]

const requestParams = () => {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - windowDays.value)
  return {from: formatBeijingTime(start), to: formatBeijingTime(end)}
}

const metricCards = computed(() => metricDefinitions.map(definition => {
  const summary = metricSummary(definition.key)
  const values = (evaluation.value.trend || [])
    .map(item => normalized(item[definition.key]))
    .filter((value): value is number => value !== null)
  const current = normalized(summary.average)
  const previous = values.length > 1 ? values[values.length - 2] : null
  const delta = current !== null && previous !== null ? current - previous : null
  const threshold = Number(summary.threshold ?? thresholdOf(definition.key))
  return {
    ...definition,
    threshold,
    score: scoreText(current),
    min: scoreText(normalized(summary.min)),
    max: scoreText(normalized(summary.max)),
    delta,
    sparkline: values,
    status: current === null ? 'empty' : current >= threshold ? 'pass' : 'warning',
  }
}))

const passRate = computed(() => {
  const completed = Number(evaluation.value.completed || 0)
  return completed ? `${Math.round(Number(evaluation.value.passed || 0) * 100 / completed)}%` : '—'
})
const lastRun = computed(() => evaluation.value.lastRunAt ? formatBeijingTime(evaluation.value.lastRunAt) : '暂无运行')
const formattedWindow = computed(() => {
  const from = evaluation.value.from ? formatBeijingTime(evaluation.value.from) : ''
  const to = evaluation.value.to ? formatBeijingTime(evaluation.value.to) : ''
  return from && to ? `${from.slice(0, 10)} 至 ${to.slice(0, 10)}` : '等待数据加载'
})
const healthState = computed(() => {
  const cards = metricCards.value
  if (!cards.some(card => card.status !== 'empty')) return 'empty'
  return cards.every(card => card.status === 'pass') ? 'healthy' : 'degraded'
})
const healthLabel = computed(() => healthState.value === 'healthy' ? '健康' : healthState.value === 'degraded' ? '下降' : '暂无数据')

function metricSummary(key: MetricKey): Partial<RagasMetricSummary> {
  return evaluation.value.metrics?.[key] || {}
}
function thresholdOf(key: MetricKey) {
  return key === 'faithfulness' ? 0.80 : key === 'answerRelevancy' ? 0.75 : 0.70
}
function normalized(value: unknown): number | null {
  const number = Number(value)
  if (!Number.isFinite(number)) return null
  return Math.max(0, Math.min(1, number > 1 ? number / 100 : number))
}
function scoreText(value: number | null) {
  return value === null ? '—' : value.toFixed(3)
}
function formatDelta(value: number | null) {
  if (value === null) return '—'
  return `${value >= 0 ? '↑' : '↓'} ${Math.abs(value).toFixed(3)}`
}
function sparkPoints(values: number[]): string {
  if (!values.length) return ''
  if (values.length === 1) return `110,${pointY(values[0])}`
  return values.map((value, index) => `${(index * 220) / (values.length - 1)},${pointY(value)}`).join(' ')
}
function sparkArea(values: number[]): string {
  const points = sparkPoints(values)
  if (!points) return ''
  const first = values.length === 1 ? 110 : 0
  const last = values.length === 1 ? 110 : 220
  return `M ${first},58 L ${points.split(' ').join(' L ')} L ${last},58 Z`
}
function pointY(value: number) { return 54 - Math.max(0, Math.min(1, value)) * 48 }
function lastPoint(values: number[]): TrendPoint {
  const value = values[values.length - 1]
  return {x: values.length === 1 ? 110 : 220, y: pointY(value)}
}
function thresholdY(value: number) { return pointY(value) }

const trendOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value: number) => Number(value).toFixed(3),
  },
  grid: {left: 46, right: 22, top: 28, bottom: 38, containLabel: true},
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: (evaluation.value.trend || []).map(item => item.period),
    axisLabel: {color: '#86909c', fontSize: 11},
    axisLine: {lineStyle: {color: '#e5e7eb'}},
  },
  yAxis: {
    type: 'value', min: 0, max: 1, interval: .25,
    axisLabel: {color: '#86909c', fontSize: 11, formatter: (value: number) => value.toFixed(2)},
    splitLine: {lineStyle: {color: '#edf0f5'}},
  },
  series: metricDefinitions.map(definition => ({
    name: definition.label,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    connectNulls: false,
    data: (evaluation.value.trend || []).map(item => normalized(item[definition.key])),
    lineStyle: {width: 2, color: definition.color},
    itemStyle: {color: definition.color},
  })),
}))

function renderTrend() {
  nextTick(() => {
    if (!trendChart.value) return
    chart ||= echarts.init(trendChart.value)
    chart.setOption(trendOption.value, true)
    chart.resize()
  })
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const {data} = await monitoringApi.dashboard(requestParams())
    evaluation.value = data.ragasEvaluation || {metrics: {} as RagasEvaluation['metrics'], trend: []}
    renderTrend()
  } catch (cause) {
    error.value = cause instanceof ApiError && cause.status === 403
      ? '当前账号无权查看 RAGAS 评估数据'
      : cause instanceof Error ? cause.message : 'RAGAS 评估数据加载失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

watch(() => evaluation.value.trend, renderTrend, {deep: true})
onMounted(() => {
  void load()
  window.addEventListener('resize', renderTrend)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', renderTrend)
  chart?.dispose()
})
</script>

<style scoped>
.ragas-page {
  --ragas-border: #e6eaf0;
  --ragas-muted: #86909c;
  --ragas-ink: #1d2129;
  --ragas-surface: #fff;
  min-height: calc(100vh - 108px);
  margin: -28px -32px -52px;
  padding: 28px 32px 52px;
  background: #f7f9fc;
}
.ragas-head { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 26px; }
.ragas-head .eyebrow { margin: 0 0 7px; color: #5478bd; font-weight: 700; letter-spacing: .12em; }
.ragas-head h1 { margin: 0; color: var(--ragas-ink); font-size: 26px; line-height: 1.3; }
.ragas-head p:last-child { margin: 7px 0 0; color: var(--ragas-muted); font-size: 13px; }
.ragas-actions { display: flex; flex: 0 0 auto; gap: 10px; }
.ragas-actions :deep(.el-select) { width: 124px; }
.ragas-actions :deep(.el-button) { min-width: 88px; }
.ragas-error { margin-bottom: 16px; }
.ragas-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 22px; border: 1px solid var(--ragas-border); border-radius: 10px; background: var(--ragas-surface); box-shadow: 0 4px 18px rgba(31, 54, 88, .045); }
.summary-item { min-width: 0; padding: 17px 22px; border-right: 1px solid #edf0f5; }
.summary-item:last-child { border-right: 0; }
.summary-item > span, .summary-item small { display: block; color: var(--ragas-muted); font-size: 12px; }
.summary-item strong { display: block; margin: 5px 0 4px; color: var(--ragas-ink); font-size: 25px; font-weight: 700; line-height: 1.1; font-variant-numeric: tabular-nums; }
.summary-item .summary-date { font-size: 18px; }
.summary-item.danger strong, .summary-item.degraded strong { color: #d34f58; }
.summary-item.healthy strong { color: #169c7f; }
.summary-item.empty strong { color: var(--ragas-muted); }
.ragas-metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 22px; }
.ragas-metric-card { min-width: 0; padding: 18px 20px 16px; border: 1px solid var(--ragas-border); border-radius: 10px; background: var(--ragas-surface); box-shadow: 0 4px 18px rgba(31, 54, 88, .045); }
.metric-topline, .metric-topline > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.metric-name { color: #4e5969; font-size: 13px; font-weight: 650; }
.metric-help { width: 17px; height: 17px; padding: 0; border: 1px solid #ccd5e3; border-radius: 50%; color: #7f8ca1; background: transparent; font-size: 11px; cursor: help; }
.metric-delta { font-size: 11px; font-weight: 650; font-variant-numeric: tabular-nums; }
.metric-delta.up { color: #159179; }.metric-delta.down { color: #d34f58; }.metric-delta:not(.up):not(.down) { color: var(--ragas-muted); }
.metric-score { display: block; min-height: 38px; margin-top: 13px; color: #d34f58; font-size: 34px; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; }
.ragas-metric-card.pass .metric-score { color: #169c7f; }.ragas-metric-card.empty .metric-score { color: #a5afbd; }
.metric-threshold { display: block; margin-top: 8px; color: #9aa5b5; font-size: 11px; }
.metric-sparkline { position: relative; height: 63px; margin: 12px 0 8px; }
.metric-sparkline svg { width: 100%; height: 58px; overflow: visible; }.metric-sparkline > span { position: absolute; inset: 20px 0 auto; color: #a4adba; font-size: 11px; text-align: center; }
.metric-stat-row { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid #edf0f5; padding-top: 10px; text-align: center; }
.metric-stat-row span { color: #a1aab7; font-size: 10px; }.metric-stat-row b { display: block; margin-top: 3px; color: #4e5969; font-size: 12px; font-variant-numeric: tabular-nums; }
.ragas-trend-card { position: relative; min-width: 0; min-height: 334px; padding: 19px 22px 15px; border: 1px solid var(--ragas-border); border-radius: 10px; background: var(--ragas-surface); box-shadow: 0 4px 18px rgba(31, 54, 88, .045); }
.trend-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }.trend-head h2 { margin: 0; color: var(--ragas-ink); font-size: 15px; }.trend-head p { margin: 6px 0 0; color: var(--ragas-muted); font-size: 11px; }
.trend-legend { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 13px; color: #687385; font-size: 11px; }.trend-legend span { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }.trend-legend i { width: 8px; height: 8px; border-radius: 2px; }
.ragas-trend-chart { height: 262px; width: 100%; }.trend-empty { position: absolute; top: 58%; left: 50%; color: #98a3b3; font-size: 12px; transform: translate(-50%, -50%); }
.metric-definitions { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }.metric-definitions article { display: flex; gap: 9px; min-width: 0; padding: 12px; border: 1px solid var(--ragas-border); border-radius: 8px; background: rgba(255,255,255,.72); }.definition-marker { width: 3px; flex: 0 0 3px; border-radius: 3px; }.metric-definitions b { color: #4e5969; font-size: 12px; }.metric-definitions p { margin: 5px 0 0; color: #86909c; font-size: 11px; line-height: 1.55; }
:global(html.dark) .ragas-page { --ragas-border: #314057; --ragas-muted: #aebbd0; --ragas-ink: #edf3ff; --ragas-surface: #182338; background: #0f1726; }.ragas-page :deep(.el-select__wrapper) { background: var(--ragas-surface); }.ragas-page :deep(.el-select__selected-item) { color: var(--ragas-ink); }
:global(html.dark) .summary-item, :global(html.dark) .metric-stat-row { border-color: #314057; }:global(html.dark) .metric-name, :global(html.dark) .metric-stat-row b, :global(html.dark) .metric-definitions b { color: #dce7fa; }:global(html.dark) .metric-definitions article { background: #182338; }
@media (max-width: 1180px) { .ragas-metric-grid, .metric-definitions { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .ragas-page { margin: -20px -14px -36px; padding: 20px 14px 36px; }.ragas-head { align-items: flex-start; flex-direction: column; gap: 16px; }.ragas-actions { width: 100%; }.ragas-actions :deep(.el-select), .ragas-actions :deep(.el-button) { flex: 1; }.ragas-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }.summary-item:nth-child(2) { border-right: 0; }.summary-item:nth-child(-n + 2) { border-bottom: 1px solid var(--ragas-border); }.summary-item { padding: 14px; }.summary-item strong { font-size: 21px; }.summary-item .summary-date { font-size: 14px; }.ragas-metric-grid, .metric-definitions { grid-template-columns: 1fr; gap: 10px; }.trend-head { flex-direction: column; }.trend-legend { justify-content: flex-start; }.ragas-trend-chart { height: 250px; }.ragas-trend-card { padding: 16px 12px; }.trend-empty { width: 80%; text-align: center; } }
@media (prefers-reduced-motion: reduce) { .ragas-page * { transition: none !important; } }
</style>
