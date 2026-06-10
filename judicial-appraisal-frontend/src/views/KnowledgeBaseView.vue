<script setup lang="ts">
import { computed, ref } from 'vue';

const knowledgeTree = [
  {
    label: '全部资料',
    key: 'all',
    children: [
      { label: '受理规范', key: '受理规范' },
      { label: '模板文件', key: '模板文件' },
      { label: '业务知识', key: '业务知识' }
    ]
  }
];

const activeCategory = ref('all');
const knowledgeItems = [
  {
    id: 1,
    title: '司法鉴定受理规范.docx',
    category: '受理规范',
    owner: '系统管理员',
    updatedAt: '2026-05-20 09:30'
  },
  {
    id: 2,
    title: '文书模板汇编.zip',
    category: '模板文件',
    owner: '张主任',
    updatedAt: '2026-05-18 16:10'
  },
  {
    id: 3,
    title: '鉴定流程常见问题.pdf',
    category: '业务知识',
    owner: '李法医',
    updatedAt: '2026-05-15 11:45'
  }
];

const filteredKnowledgeItems = computed(() => {
  if (activeCategory.value === 'all') {
    return knowledgeItems;
  }
  return knowledgeItems.filter((item) => item.category === activeCategory.value);
});

function handleTreeSelect(_: unknown, node: { key: string }): void {
  activeCategory.value = node.key;
}
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">知识库</h3>
        <p class="panel-subtitle">集中保存业务规范、模板文件和常见知识材料。</p>
      </div>
      <p class="section-note">优先查看最新更新的制度文件和模板。</p>
    </div>

    <div class="knowledge-layout">
      <aside class="knowledge-tree-panel">
        <div class="knowledge-tree-header">分类目录</div>
        <el-tree
          class="knowledge-tree"
          :data="knowledgeTree"
          node-key="key"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @current-change="handleTreeSelect"
        />
      </aside>

      <div class="knowledge-main">
        <div class="knowledge-toolbar">
          <p class="knowledge-toolbar-text">当前收录 {{ filteredKnowledgeItems.length }} 份材料，支持查看与下载。</p>
          <p class="knowledge-toolbar-text">当前分类：{{ activeCategory === 'all' ? '全部资料' : activeCategory }}</p>
        </div>

        <div class="table-frame">
          <el-table :data="filteredKnowledgeItems" border stripe>
            <el-table-column prop="title" label="标题" min-width="280">
              <template #default="scope">
                <div class="knowledge-title">
                  <span class="primary-text">{{ scope.row.title }}</span>
                  <span class="knowledge-category">{{ scope.row.category }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="owner" label="上传人" width="140" />
            <el-table-column prop="updatedAt" label="更新时间" width="180" />
            <el-table-column label="操作" width="170">
              <template #default>
                <div class="inline-actions">
                  <el-button link type="primary">查看</el-button>
                  <el-button link type="primary">下载</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </section>
</template>
