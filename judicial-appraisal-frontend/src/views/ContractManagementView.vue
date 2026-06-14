<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus';

import {
  approveContract,
  createContract,
  fetchContractDetail,
  fetchContracts,
  rejectContract,
  submitContract,
  updateContract,
  uploadContractFile,
  type ContractItem,
  type ContractPayload
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();

const loading = ref(false);
const saving = ref(false);
const uploading = ref(false);
const contracts = ref<ContractItem[]>([]);
const selectedContract = ref<ContractItem | null>(null);
const detailVisible = ref(false);
const editorVisible = ref(false);
const editingContract = ref<ContractItem | null>(null);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const filters = reactive({
  keyword: '',
  status: 'all'
});

const form = reactive({
  contractName: '',
  customerName: '',
  relatedCaseId: '',
  amount: 0,
  departmentId: '',
  departmentName: '',
  content: '',
  changeNote: '',
  fileIdsText: ''
});

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '草稿', value: 'DRAFT' },
  { label: '审核中', value: 'UNDER_REVIEW' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已归档', value: 'ARCHIVED' }
];

const statusTone: Record<string, string> = {
  DRAFT: 'info',
  UNDER_REVIEW: 'warning',
  REJECTED: 'danger',
  ARCHIVED: 'success'
};

const canEdit = computed(() => {
  if (!selectedContract.value) return false;
  return ['DRAFT', 'REJECTED'].includes(selectedContract.value.status) && isOwnerOrAdmin(selectedContract.value);
});

const canSubmit = computed(() => canEdit.value);
const canReview = computed(() => {
  const contract = selectedContract.value;
  if (!contract || contract.status !== 'UNDER_REVIEW') return false;
  return authStore.isAdmin || (authStore.user?.deptId != null && authStore.user.deptId === contract.departmentId);
});

function isOwnerOrAdmin(contract: ContractItem): boolean {
  return authStore.isAdmin || authStore.user?.id === contract.ownerId;
}

function resetForm(contract?: ContractItem): void {
  editingContract.value = contract ?? null;
  form.contractName = contract?.contractName ?? '';
  form.customerName = contract?.customerName ?? '';
  form.relatedCaseId = contract?.relatedCaseId ? String(contract.relatedCaseId) : '';
  form.amount = Number(contract?.amount ?? 0);
  form.departmentId = contract?.departmentId ? String(contract.departmentId) : '';
  form.departmentName = contract?.departmentName ?? authStore.user?.deptName ?? '';
  form.content = contract?.latestVersion?.content ?? '';
  form.changeNote = contract ? '合同修改' : '合同创建';
  form.fileIdsText = contract?.attachments.map((item) => String(item.fileId)).join(', ') ?? '';
}

function toPayload(): ContractPayload {
  const fileIds = form.fileIdsText
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0);
  return {
    contractName: form.contractName.trim(),
    customerName: form.customerName.trim(),
    relatedCaseId: form.relatedCaseId ? Number(form.relatedCaseId) : null,
    amount: Number(form.amount || 0),
    departmentId: form.departmentId ? Number(form.departmentId) : authStore.user?.deptId ?? null,
    departmentName: form.departmentName.trim() || authStore.user?.deptName || null,
    content: form.content,
    changeNote: form.changeNote.trim() || null,
    fileIds
  };
}

async function loadContracts(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchContracts({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status === 'all' ? undefined : filters.status,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    contracts.value = result.records;
    total.value = result.total;
    if (selectedContract.value) {
      const matched = contracts.value.find((item) => item.id === selectedContract.value?.id);
      if (matched) selectedContract.value = matched;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载合同失败');
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: ContractItem): Promise<void> {
  try {
    selectedContract.value = await fetchContractDetail(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载合同详情失败');
  }
}

function openCreate(): void {
  resetForm();
  editorVisible.value = true;
}

function openEdit(): void {
  if (!selectedContract.value) return;
  resetForm(selectedContract.value);
  editorVisible.value = true;
}

async function saveContract(): Promise<void> {
  if (!form.contractName.trim() || !form.customerName.trim() || !form.content.trim()) {
    ElMessage.warning('请填写合同名称、客户名称和合同内容');
    return;
  }
  saving.value = true;
  try {
    const payload = toPayload();
    const saved = editingContract.value
      ? await updateContract(editingContract.value.id, payload)
      : await createContract(payload);
    ElMessage.success(editingContract.value ? '合同已更新' : '合同已创建');
    editorVisible.value = false;
    selectedContract.value = saved;
    detailVisible.value = true;
    await loadContracts();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存合同失败');
  } finally {
    saving.value = false;
  }
}

async function uploadAttachment(options: UploadRequestOptions): Promise<void> {
  uploading.value = true;
  try {
    const response = await uploadContractFile(options.file as File, editingContract.value?.id);
    const existing = form.fileIdsText.trim();
    form.fileIdsText = existing ? `${existing}, ${response.fileId}` : String(response.fileId);
    ElMessage.success(`已上传：${response.originalName}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '附件上传失败');
  } finally {
    uploading.value = false;
  }
}

async function handleSubmit(): Promise<void> {
  if (!selectedContract.value) return;
  try {
    selectedContract.value = await submitContract(selectedContract.value.id);
    ElMessage.success('已提交部门审核');
    await loadContracts();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败');
  }
}

async function handleApprove(): Promise<void> {
  if (!selectedContract.value) return;
  const { value } = await ElMessageBox.prompt('请输入审核意见', '部门审核通过', {
    confirmButtonText: '通过',
    cancelButtonText: '取消',
    inputValue: '部门审核通过'
  });
  try {
    selectedContract.value = await approveContract(selectedContract.value.id, value);
    ElMessage.success('合同已审批并归档知识库');
    await loadContracts();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批失败');
  }
}

async function handleReject(): Promise<void> {
  if (!selectedContract.value) return;
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '部门审核驳回', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputValue: '资料需补充'
  });
  try {
    selectedContract.value = await rejectContract(selectedContract.value.id, value);
    ElMessage.success('合同已驳回');
    await loadContracts();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回失败');
  }
}

function handlePageChange(page: number): void {
  pageNo.value = page;
  void loadContracts();
}

onMounted(() => {
  void loadContracts();
});
</script>

<template>
  <section class="content-card page-block contract-page">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">合同管理</h3>
        <p class="panel-subtitle">第五阶段第一条业务闭环：创建、提交、部门审核、知识库归档和审计留痕。</p>
      </div>
      <div class="inline-actions">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="合同编号 / 名称 / 客户"
          style="width: 260px"
          @keyup.enter="loadContracts"
          @clear="loadContracts"
        />
        <el-select v-model="filters.status" style="width: 140px" @change="loadContracts">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :loading="loading" @click="loadContracts">查询</el-button>
        <el-button type="primary" @click="openCreate">新建合同</el-button>
      </div>
    </div>

    <div class="table-frame">
      <el-table v-loading="loading" :data="contracts" border stripe @row-dblclick="openDetail">
        <el-table-column prop="contractNo" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="220" />
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="amount" label="金额" width="130">
          <template #default="scope">{{ Number(scope.row.amount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" width="150" />
        <el-table-column prop="ownerName" label="负责人" width="120" />
        <el-table-column prop="statusName" label="状态" width="110">
          <template #default="scope">
            <el-tag :type="statusTone[scope.row.status] || 'info'">{{ scope.row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pager-line">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" size="520px" title="合同详情">
      <template v-if="selectedContract">
        <div class="contract-detail-head">
          <div>
            <p class="contract-no">{{ selectedContract.contractNo }}</p>
            <h3>{{ selectedContract.contractName }}</h3>
          </div>
          <el-tag :type="statusTone[selectedContract.status] || 'info'">{{ selectedContract.statusName }}</el-tag>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="客户">{{ selectedContract.customerName }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ Number(selectedContract.amount || 0).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ selectedContract.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ selectedContract.departmentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联案件">{{ selectedContract.relatedCaseId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="知识库归档">
            <span v-if="selectedContract.archiveDocumentId">文档 #{{ selectedContract.archiveDocumentId }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="审核意见">{{ selectedContract.reviewOpinion || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="contract-section">
          <div class="contract-section-title">合同内容 v{{ selectedContract.latestVersion?.versionNo || '-' }}</div>
          <pre>{{ selectedContract.latestVersion?.content || '暂无内容' }}</pre>
        </div>

        <div class="contract-section">
          <div class="contract-section-title">附件</div>
          <el-empty v-if="selectedContract.attachments.length === 0" description="暂无附件" :image-size="72" />
          <el-table v-else :data="selectedContract.attachments" border>
            <el-table-column prop="fileName" label="文件名" min-width="180" />
            <el-table-column prop="fileId" label="文件ID" width="100" />
          </el-table>
        </div>

        <div class="drawer-actions">
          <el-button v-if="canEdit" @click="openEdit">编辑</el-button>
          <el-button v-if="canSubmit" type="primary" @click="handleSubmit">提交审批</el-button>
          <el-button v-if="canReview" type="success" @click="handleApprove">审批通过</el-button>
          <el-button v-if="canReview" type="danger" @click="handleReject">驳回</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="editorVisible" :title="editingContract ? '编辑合同' : '新建合同'" width="720px">
      <el-form label-position="top">
        <div class="contract-form-grid">
          <el-form-item label="合同名称">
            <el-input v-model="form.contractName" />
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input v-model="form.customerName" />
          </el-form-item>
          <el-form-item label="合同金额">
            <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="关联案件ID">
            <el-input v-model="form.relatedCaseId" placeholder="可选" />
          </el-form-item>
          <el-form-item label="部门ID">
            <el-input v-model="form.departmentId" placeholder="默认当前部门" />
          </el-form-item>
          <el-form-item label="部门名称">
            <el-input v-model="form.departmentName" placeholder="默认当前部门" />
          </el-form-item>
        </div>
        <el-form-item label="合同内容">
          <el-input v-model="form.content" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="form.changeNote" />
        </el-form-item>
        <el-form-item label="附件文件ID">
          <div class="attachment-line">
            <el-input v-model="form.fileIdsText" placeholder="可输入已有文件ID，多个用逗号分隔" />
            <el-upload :show-file-list="false" :http-request="uploadAttachment">
              <el-button :loading="uploading">上传附件</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveContract">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.contract-page {
  min-height: calc(100vh - 140px);
}

.pager-line {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.contract-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.contract-detail-head h3 {
  margin: 4px 0 0;
  font-size: 18px;
  line-height: 1.4;
}

.contract-no {
  margin: 0;
  color: var(--muted-text);
  font-size: 13px;
}

.contract-section {
  margin-top: 18px;
}

.contract-section-title {
  margin-bottom: 8px;
  color: var(--strong-text);
  font-weight: 700;
}

.contract-section pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--soft-surface);
  color: var(--strong-text);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 22px;
}

.contract-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.attachment-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

@media (max-width: 760px) {
  .contract-form-grid,
  .attachment-line {
    grid-template-columns: 1fr;
  }
}
</style>
