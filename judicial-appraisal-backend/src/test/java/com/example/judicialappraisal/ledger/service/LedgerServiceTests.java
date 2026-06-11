package com.example.judicialappraisal.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.ledger.dto.LedgerBoardDto;
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

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(caseInfoMapper, null, null, null, null, null, null, null, null, null, null);
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
}
