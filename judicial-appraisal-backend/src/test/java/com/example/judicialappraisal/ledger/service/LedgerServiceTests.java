package com.example.judicialappraisal.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.audit.entity.AuditEvent;
import com.example.judicialappraisal.audit.mapper.AuditEventMapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.knowledge.entity.CaseArchiveRecord;
import com.example.judicialappraisal.knowledge.mapper.CaseArchiveRecordMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.judicialappraisal.ledger.dto.LedgerBoardDto;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTests {

    @Mock
    private CaseInfoMapper caseInfoMapper;
    @Mock
    private CaseArchiveRecordMapper caseArchiveRecordMapper;
    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysMenuMapper sysMenuMapper;
    @Mock
    private AuditEventMapper auditEventMapper;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(caseInfoMapper, null, sysRoleMapper, sysMenuMapper, auditEventMapper,
                caseArchiveRecordMapper, knowledgeDocumentMapper, null, null, null, null);
    }

    @Test
    void crmBoardAggregatesEntrustOrganizationsFromLiveCases() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of(
                caseInfo(1L, "沪司鉴-001", "交通事故鉴定", "PROCESSING", "上海市某法院", "综合业务部", "张主任", 1, LocalDateTime.now().plusDays(1)),
                caseInfo(2L, "沪司鉴-002", "房屋损失鉴定", "COMPLETED", "上海市某法院", "综合业务部", "张主任", 0, LocalDateTime.now().plusDays(3)),
                caseInfo(3L, "沪司鉴-003", "医疗损害鉴定", "REVIEWING", "某保险公估公司", "司法鉴定一部", "李经理", 0, LocalDateTime.now().plusDays(2))
        ));

        LedgerBoardDto board = ledgerService.board("crm", null, null, 10);

        assertThat(board.sourceType()).isEqualTo("live");
        assertThat(board.metrics()).extracting("label", "value")
                .contains(tuple("委托单位", "2"), tuple("跟进中客户", "2"), tuple("累计委托", "3"));
        assertThat(board.statusOptions()).contains("all", "active", "urgent", "stabilized");
        assertThat(board.rows()).hasSize(2);
        assertThat(board.rows().get(0).primaryText()).isEqualTo("某保险公估公司");
        assertThat(board.rows()).anySatisfy(row -> {
            if ("上海市某法院".equals(row.primaryText())) {
                assertThat(row.metricText()).contains("委托案件 2 件", "紧急 1 件");
                assertThat(row.facts()).contains("活跃案件数：1");
                assertThat(row.facts()).anyMatch(item -> item.startsWith("客户分级："));
                assertThat(row.facts()).anyMatch(item -> item.startsWith("建议跟进时间："));
                assertThat(row.relatedPath()).isEqualTo("/case/1");
            }
        });
    }

    @Test
    void contractBoardFallsBackToSampleRowsWhenNoCasesExist() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of());

        LedgerBoardDto board = ledgerService.board("contract", null, null, 10);

        assertThat(board.sourceType()).isEqualTo("sample");
        assertThat(board.rows()).isNotEmpty();
        assertThat(board.rows().get(0).tertiaryText()).isNotBlank();
        assertThat(board.rows().get(0).relatedPath()).startsWith("/case/");
        assertThat(board.metrics()).extracting("label")
                .contains("合同清单", "审批中", "履约中", "已收口");
    }

    @Test
    void projectBoardMarksOverdueCasesAsWarningStatus() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of(
                caseInfo(10L, "沪司鉴-010", "设备损坏鉴定", "PROCESSING", "某律所", "司法鉴定二部", "王主管", 1, LocalDateTime.now().minusDays(1))
        ));

        LedgerBoardDto board = ledgerService.board("project", null, null, 10);

        assertThat(board.sourceType()).isEqualTo("live");
        assertThat(board.metrics()).extracting("label", "value").contains(tuple("已超期", "1"));
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("预警中");
        assertThat(board.rows().get(0).tags()).contains("超期", "紧急");
        assertThat(board.rows().get(0).actionHint()).contains("优先处理超期说明");
        assertThat(board.rows().get(0).relatedPath()).isEqualTo("/case/10");
        assertThat(board.rows().get(0).facts()).contains("案件编号：沪司鉴-010");
        assertThat(board.rows().get(0).facts()).anyMatch(item -> item.startsWith("项目里程碑："));
        assertThat(board.rows().get(0).facts()).anyMatch(item -> item.startsWith("下一检查点："));
    }

    @Test
    void archiveBoardSupportsStatusFilteringAndKnowledgeNavigation() {
        when(caseArchiveRecordMapper.selectList(any())).thenReturn(List.of(
                archiveRecord(1L, 11L, "沪司鉴-011", "archived", "意见书归档"),
                archiveRecord(2L, 12L, "沪司鉴-012", "pending", null)
        ));
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(5L);

        LedgerBoardDto board = ledgerService.board("archive", null, "archived", 10);

        assertThat(board.statusOptions()).contains("all", "archived", "pending");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("已入库");
        assertThat(board.rows().get(0).relatedPath()).isEqualTo("/knowledge");
    }

    @Test
    void systemPermissionBoardSupportsStatusFilteringAndUserNavigation() {
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(
                role(1L, "管理员", "ADMIN", "enabled", "all"),
                role(2L, "访客", "GUEST", "disabled", "self")
        ));
        when(sysMenuMapper.selectCount(any())).thenReturn(12L);

        LedgerBoardDto board = ledgerService.board("system-permission", null, "disabled", 10);

        assertThat(board.statusOptions()).contains("all", "enabled", "disabled");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("停用");
        assertThat(board.rows().get(0).relatedPath()).isEqualTo("/admin/users");
    }

    @Test
    void systemLogBoardSupportsFailureFilteringAndCaseNavigation() {
        when(auditEventMapper.selectList(any())).thenReturn(List.of(
                auditEvent(1L, "提交案件", "success", 21L),
                auditEvent(2L, "终止流程", "failed", 22L)
        ));

        LedgerBoardDto board = ledgerService.board("system-log", null, "failed", 10);

        assertThat(board.statusOptions()).contains("all", "success", "failed");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("失败");
        assertThat(board.rows().get(0).relatedPath()).isEqualTo("/case/22");
    }

    @Test
    void noticeBoardProvidesStructuredStatusFiltering() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of());

        LedgerBoardDto board = ledgerService.board("notice", null, "draft", 10);

        assertThat(board.sourceType()).isEqualTo("structured");
        assertThat(board.statusOptions()).contains("all", "published", "draft", "pending");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("草稿");
        assertThat(board.rows().get(0).facts()).anyMatch(item -> item.contains("发布对象"));
    }

    @Test
    void attendanceBoardProvidesExceptionAndLeaveFilters() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of());

        LedgerBoardDto board = ledgerService.board("attendance", null, "exception", 10);

        assertThat(board.sourceType()).isEqualTo("structured");
        assertThat(board.statusOptions()).contains("all", "normal", "exception", "leave");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).primaryText()).isEqualTo("李经理");
        assertThat(board.rows().get(0).facts()).contains("异常类型：漏打卡");
    }

    @Test
    void openApiBoardProvidesWarningIntegrationFacts() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of());

        LedgerBoardDto board = ledgerService.board("open-api", null, "warning", 10);

        assertThat(board.sourceType()).isEqualTo("structured");
        assertThat(board.statusOptions()).contains("all", "online", "warning", "draft");
        assertThat(board.rows()).hasSize(1);
        assertThat(board.rows().get(0).statusLabel()).isEqualTo("预警");
        assertThat(board.rows().get(0).facts()).contains("关联流程：seal-application");
    }

    @Test
    void warehouseAndRiskBoardsSupportStatusFiltering() {
        when(caseInfoMapper.selectList(any())).thenReturn(List.of(
                caseInfo(10L, "沪司鉴-010", "设备损坏鉴定", "PROCESSING", "某律所", "司法鉴定二部", "王主管", 1, LocalDateTime.now().minusDays(1))
        ));

        LedgerBoardDto warehouse = ledgerService.board("warehouse", null, "borrowed", 10);
        LedgerBoardDto risk = ledgerService.board("risk", null, "overdue", 10);

        assertThat(warehouse.statusOptions()).contains("borrowed", "inbound", "stock");
        assertThat(warehouse.rows()).hasSize(1);
        assertThat(warehouse.rows().get(0).statusLabel()).isEqualTo("待归还");
        assertThat(risk.statusOptions()).contains("all", "overdue", "urgent");
        assertThat(risk.rows()).hasSize(1);
        assertThat(risk.rows().get(0).statusLabel()).isEqualTo("预警中");
    }

    private CaseInfo caseInfo(Long id, String caseNo, String caseTitle, String status, String entrustOrgName,
                              String acceptDeptName, String currentHandlerName, Integer urgentFlag,
                              LocalDateTime deadlineTime) {
        CaseInfo item = new CaseInfo();
        item.setId(id);
        item.setCaseNo(caseNo);
        item.setCaseTitle(caseTitle);
        item.setCaseStatus(status);
        item.setEntrustOrgName(entrustOrgName);
        item.setAcceptDeptName(acceptDeptName);
        item.setCurrentHandlerName(currentHandlerName);
        item.setUrgentFlag(urgentFlag);
        item.setDeadlineTime(deadlineTime);
        item.setUpdatedTime(LocalDateTime.now());
        item.setCreatedTime(LocalDateTime.now().minusDays(2));
        return item;
    }

    private CaseArchiveRecord archiveRecord(Long id, Long caseId, String caseNo, String status, String summary) {
        CaseArchiveRecord item = new CaseArchiveRecord();
        item.setId(id);
        item.setCaseId(caseId);
        item.setCaseNo(caseNo);
        item.setArchiveStatus(status);
        item.setArchiveSummary(summary);
        item.setArchiveType("案件档案");
        item.setNodeName("归档");
        item.setTaskId(100L + id);
        item.setDocumentId(200L + id);
        item.setArchivedBy(1L);
        item.setArchivedTime(LocalDateTime.now().minusHours(id));
        return item;
    }

    private SysRole role(Long id, String name, String code, String status, String dataScope) {
        SysRole item = new SysRole();
        item.setId(id);
        item.setRoleName(name);
        item.setRoleCode(code);
        item.setStatus(status);
        item.setDataScope(dataScope);
        item.setCreatedBy(1L);
        item.setCreatedTime(LocalDateTime.now().minusDays(2));
        item.setUpdatedTime(LocalDateTime.now());
        return item;
    }

    private AuditEvent auditEvent(Long id, String actionName, String resultStatus, Long caseId) {
        AuditEvent item = new AuditEvent();
        item.setId(id);
        item.setActionName(actionName);
        item.setActionCode("ACTION-" + id);
        item.setResultStatus(resultStatus);
        item.setCaseId(caseId);
        item.setBizType("workflow");
        item.setBizId(1000L + id);
        item.setOperatorName("管理员");
        item.setIpAddress("127.0.0.1");
        item.setOperatedTime(LocalDateTime.now().minusMinutes(id));
        return item;
    }
}
