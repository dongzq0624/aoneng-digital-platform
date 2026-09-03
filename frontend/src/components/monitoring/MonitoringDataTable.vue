<template>
  <div class="table-wrap">
    <div class="section-title">{{ title }}</div>
    <table v-if="rows.length">
      <thead>
      <tr>
        <th>文档编号</th>
        <th>文件名</th>
        <th>状态</th>
        <th>原因</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="row in rows" :key="row.docId">
        <slot :row="row"/>
      </tr>
      </tbody>
    </table>
    <div v-else class="empty-inline">{{ emptyText }}</div>
  </div>
</template>
<script setup lang="ts">
defineProps<{ title: string; rows: Record<string, any>[]; emptyText: string; columns?: string[] }>()
</script>
<style scoped>
.table-wrap {
  overflow-x: auto;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid #e4e3dd;
  border-radius: 10px;
  padding: 14px
}

.section-title {
  font-size: 13px;
  font-weight: 650;
  color: #1a1b1c;
  margin-bottom: 8px
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
  color: #6b7280;
  font-weight: 600;
  border-bottom: 1px solid #e4e3dd;
  white-space: nowrap
}

td {
  padding: 9px 10px;
  border-bottom: 1px solid #f0f0ed;
  color: #1a1b1c
}

.empty-inline {
  padding: 34px 12px;
  text-align: center;
  color: #9aa1ad;
  font-size: 12px
}
</style>
