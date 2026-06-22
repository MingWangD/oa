<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';

import {
  downloadKnowledgeDocument,
  fetchKnowledgeDirectories,
  fetchKnowledgeDocuments,
  previewKnowledgeDocument,
  uploadManualDocument,
  deleteKnowledgeDocument,
  batchDeleteKnowledgeDocuments,
  type KnowledgeDirectory,
  type KnowledgeDocument
} from '../api/judicial';
import { getAccessToken } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { ElMessageBox } from 'element-plus';

interface TreeNode {
  label: string;
  key: string;
  directoryId?: number;
  children?: TreeNode[];
}

const authStore = useAuthStore();
const loading = ref(false);
const batchDownloading = ref(false);
const directories = ref<KnowledgeDirectory[]>([]);
const documents = ref<KnowledgeDocument[]>([]);
const selectedDocuments = ref<KnowledgeDocument[]>([]);
const activeDirectoryId = ref<number | undefined>();
const keyword = ref('');

const currentPage = ref(1);
const pageSize = ref(10);

const paginatedDocuments = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return documents.value.slice(start, end);
});

function handlePageChange(val: number): void {
  currentPage.value = val;
}

const treeData = computed<TreeNode[]>(() => {
  const nodeMap = new Map<number, TreeNode>();
  const roots: TreeNode[] = [];
  directories.value.forEach((directory) => {
    nodeMap.set(directory.id, {
      label: directory.directoryName,
      key: String(directory.id),
      directoryId: directory.id,
      children: []
    });
  });
  directories.value.forEach((directory) => {
    const node = nodeMap.get(directory.id);
    if (!node) {
      return;
    }
    if (directory.parentId && nodeMap.has(directory.parentId)) {
      nodeMap.get(directory.parentId)?.children?.push(node);
    } else {
      roots.push(node);
    }
  });
  return [{ label: '全部资料', key: 'all', children: roots }];
});

const activeDirectoryName = computed(() => {
  if (!activeDirectoryId.value) {
    return '全部资料';
  }
  return directories.value.find((item) => item.id === activeDirectoryId.value)?.directoryName || '全部资料';
});

async function loadDirectories(): Promise<void> {
  directories.value = await fetchKnowledgeDirectories();
}

async function loadDocuments(): Promise<void> {
  loading.value = true;
  try {
    documents.value = await fetchKnowledgeDocuments({
      directoryId: activeDirectoryId.value,
      keyword: keyword.value
    });
    currentPage.value = 1;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载知识文档失败');
  } finally {
    loading.value = false;
  }
}

async function refresh(): Promise<void> {
  loading.value = true;
  try {
    await loadDirectories();
    await loadDocuments();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载知识库失败');
  } finally {
    loading.value = false;
  }
}

function handleTreeSelect(data: TreeNode): void {
  activeDirectoryId.value = data.key === 'all' ? undefined : data.directoryId;
  void loadDocuments();
}

async function openPreview(row: KnowledgeDocument): Promise<void> {
  if (!row.currentFileId) {
    ElMessage.info('该记录是流程节点归档，暂无可预览文件');
    return;
  }
  try {
    const { blob } = await previewKnowledgeDocument(row.id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预览失败');
  }
}

async function download(row: KnowledgeDocument): Promise<void> {
  if (!row.currentFileId) {
    ElMessage.info('该记录是流程节点归档，暂无可下载文件');
    return;
  }
  try {
    const { blob, filename } = await downloadKnowledgeDocument(row.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败');
  }
}

function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '';
  const date = new Date(isoString);
  const m = date.getMonth() + 1;
  const d = date.getDate();
  const w = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()];
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  return `${m}月${d}日 ${w} ${hh}:${mm}:${ss}`;
}

async function handleDelete(row: KnowledgeDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定要删除记录 "${row.title}" 吗？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    
    await deleteKnowledgeDocument(row.id);
    ElMessage.success('删除成功');
    void refresh();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

async function batchDelete(): Promise<void> {
  if (selectedDocuments.value.length === 0) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定要批量删除选中的 ${selectedDocuments.value.length} 条记录吗？此操作不可恢复。`,
      '确认批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    
    const ids = selectedDocuments.value.map((doc) => doc.id);
    await batchDeleteKnowledgeDocuments(ids);
    ElMessage.success('批量删除成功');
    selectedDocuments.value = [];
    void refresh();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
    }
  }
}

async function batchDownload(): Promise<void> {
  const downloadable = selectedDocuments.value.filter((item) => item.currentFileId);
  if (!downloadable.length) {
    ElMessage.warning('请先选择有文件的归档文档');
    return;
  }
  batchDownloading.value = true;
  try {
    for (const row of downloadable) {
      await download(row);
    }
    ElMessage.success(`已触发 ${downloadable.length} 个文件下载`);
  } finally {
    batchDownloading.value = false;
  }
}

function handleSelectionChange(rows: KnowledgeDocument[]): void {
  selectedDocuments.value = rows;
}

const uploadHeaders = computed(() => {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
});

async function onUploadSuccess(response: any): Promise<void> {
  if (response && response.data) {
    const fileId = response.data.id;
    const originalName = response.data.originalName;
    try {
      await uploadManualDocument({
        directoryId: activeDirectoryId.value!,
        title: originalName,
        fileId
      });
      ElMessage.success('手动上传并归档成功');
      void loadDocuments();
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '手动归档失败');
    }
  } else {
    ElMessage.error(response?.message || '文件上传失败');
  }
}

function onUploadError(error: any): void {
  ElMessage.error(error instanceof Error ? error.message : '文件上传失败');
}

function handleRowClick(row: KnowledgeDocument, column: any, event: Event): void {
  if (column.type === 'selection' || (event.target as HTMLElement).closest('.el-checkbox')) {
    return;
  }
  void openPreview(row);
}

onMounted(() => {
  void refresh();
});
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">知识库</h3>
      </div>
      <div class="inline-actions">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索标题"
          style="width: 220px"
          @keyup.enter="loadDocuments"
          @clear="loadDocuments"
        />
        <el-button type="primary" :loading="loading" @click="loadDocuments">查询</el-button>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </div>
    </div>

    <div class="knowledge-layout">
      <aside class="knowledge-tree-panel">
        <div class="knowledge-tree-header">分类目录</div>
        <el-tree
          class="knowledge-tree"
          :data="treeData"
          node-key="key"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @current-change="handleTreeSelect"
        />
      </aside>

      <div class="knowledge-main">
        <div class="knowledge-toolbar">
          <p class="knowledge-toolbar-text">当前收录 {{ documents.length }} 份材料</p>
          <p class="knowledge-toolbar-text">当前分类：{{ activeDirectoryName }}</p>
          <el-button
            type="primary"
            :disabled="!selectedDocuments.some((item) => item.currentFileId)"
            :loading="batchDownloading"
            @click="batchDownload"
          >
            批量下载
          </el-button>
          <el-button
            v-if="authStore.isAdmin"
            type="danger"
            :disabled="selectedDocuments.length === 0"
            style="margin-left: 12px"
            @click="batchDelete"
          >
            批量删除
          </el-button>
          <el-upload
            style="display: inline-block; margin-left: 12px"
            action="/api/files/upload"
            name="file"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
            :disabled="!activeDirectoryId"
          >
            <el-button type="success" :disabled="!activeDirectoryId">手动上传</el-button>
          </el-upload>
        </div>

        <div class="table-frame">
          <el-table
            v-loading="loading"
            :data="paginatedDocuments"
            border
            stripe
            @selection-change="handleSelectionChange"
            @row-click="handleRowClick"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="title" label="标题" min-width="280">
              <template #default="scope">
                <div class="knowledge-title">
                  <span class="primary-text">{{ scope.row.title }}</span>
                  <span class="knowledge-category">{{ scope.row.sourceType === 'archive' ? '自动归档' : '知识文档' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="nodeName" label="流程节点" width="180" />
            <el-table-column prop="updatedTime" label="更新时间" width="220">
              <template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="scope">
                <div class="inline-actions">
                  <el-button link type="primary" @click.stop="openPreview(scope.row)">查看</el-button>
                  <el-button link type="primary" @click.stop="download(scope.row)">下载</el-button>
                  <el-button v-if="authStore.isAdmin" link type="danger" @click.stop="handleDelete(scope.row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="documents.length > 0" class="pager-bar" style="margin-top: 16px;">
          <p class="pager-text">第 {{ currentPage }} 页，每页 {{ pageSize }} 条，共 {{ documents.length }} 条</p>
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="currentPage"
            :page-size="pageSize"
            :total="documents.length"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
