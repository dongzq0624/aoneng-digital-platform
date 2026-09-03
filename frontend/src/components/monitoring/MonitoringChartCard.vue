<template>
  <div class="chart-box" :class="{large}">
    <div class="section-title">{{ title }}<small v-if="subtitle">{{ subtitle }}</small></div>
    <div class="chart-container" :ref="el => setRef(chartId, el)"></div>
  </div>
</template>
<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
  chartId: string;
  setRef: (id: string, el: unknown) => void;
  large?: boolean;
  option?: Record<string, unknown>;
  empty?: boolean
}>()
</script>
<style scoped>
.chart-box {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4e3dd;
  border-radius: 10px;
  padding: 14px
}

.chart-container {
  height: 248px;
  width: 100%
}

.chart-box.large .chart-container {
  height: 350px
}

.section-title {
  font-size: 13px;
  font-weight: 650;
  color: #1a1b1c;
  margin-bottom: 8px
}

.section-title small {
  margin-left: 8px;
  color: #6b7280;
  font-size: 11px;
  font-weight: 400
}
</style>
