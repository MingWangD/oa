<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => String(route.meta.title || '待建设模块'));
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">{{ title }}</h3>
        <p class="panel-subtitle">该模块已纳入完整 OA 重构范围，当前阶段先完成平台底座和目录承载。</p>
      </div>
    </div>
    <div class="empty-state">
      <p>后续会按需求优先级逐步接入业务台账、流程、权限、审计和报表。</p>
    </div>
  </section>
</template>
