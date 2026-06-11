package com.example.judicialappraisal.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.audit.entity.AuditEvent;
import com.example.judicialappraisal.audit.mapper.AuditEventMapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.knowledge.entity.CaseArchiveRecord;
import com.example.judicialappraisal.knowledge.entity.KnowledgeDocument;
import com.example.judicialappraisal.knowledge.mapper.CaseArchiveRecordMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.judicialappraisal.ledger.dto.LedgerBoardDto;
import com.example.judicialappraisal.ledger.dto.LedgerMetricDto;
import com.example.judicialappraisal.ledger.dto.LedgerRowDto;
import com.example.judicialappraisal.organization.entity.SysMenu;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUser;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private static final int DEFAULT_LIMIT = 12;

    private final CaseInfoMapper caseInfoMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final AuditEventMapper auditEventMapper;
    private final CaseArchiveRecordMapper caseArchiveRecordMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public LedgerService(CaseInfoMapper caseInfoMapper) {
        this(caseInfoMapper, null, null, null, null, null, null);
    }

    public LedgerService(CaseInfoMapper caseInfoMapper,
                         SysUserMapper sysUserMapper,
                         SysRoleMapper sysRoleMapper,
                         SysMenuMapper sysMenuMapper,
                         AuditEventMapper auditEventMapper,
                         CaseArchiveRecordMapper caseArchiveRecordMapper,
                         KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.caseInfoMapper = caseInfoMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.auditEventMapper = auditEventMapper;
        this.caseArchiveRecordMapper = caseArchiveRecordMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
    }

    public LedgerBoardDto board(String moduleCode, String keyword, String status, Integer limit) {
        String normalizedCode = normalizeModuleCode(moduleCode);
        List<CaseInfo> cases = loadCases(keyword);
        int rowLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 50);
        String normalizedStatus = normalizeModuleCode(status);

        return switch (normalizedCode) {
            case "quick-mail" -> quickMailBoard();
            case "quick-calendar" -> quickCalendarBoard();
            case "personal-message" -> personalMessageBoard();
            case "personal-task" -> personalTaskBoard(cases, rowLimit);
            case "personal-log" -> personalLogBoard();
            case "personal-address" -> personalAddressBoard();
            case "application-mine" -> applicationMineBoard();
            case "application-design" -> applicationDesignBoard();
            case "crm" -> crmBoard(cases, normalizedStatus, rowLimit);
            case "performance" -> performanceBoard(cases, rowLimit);
            case "contract" -> contractBoard(cases, normalizedStatus, rowLimit);
            case "project" -> projectBoard(cases, normalizedStatus, rowLimit);
            case "warehouse" -> warehouseBoard();
            case "risk" -> riskBoard(cases, rowLimit);
            case "notice" -> noticeBoard();
            case "meeting" -> meetingBoard();
            case "asset" -> assetBoard();
            case "hr" -> hrBoard(rowLimit);
            case "attendance" -> attendanceBoard();
            case "official-doc" -> officialDocBoard();
            case "archive" -> archiveBoard(rowLimit);
            case "community" -> communityBoard();
            case "supervision" -> supervisionBoard(cases, rowLimit);
            case "portal" -> portalBoard(cases);
            case "report-center" -> reportCenterBoard(cases);
            case "open-api" -> openApiBoard();
            case "sso" -> ssoBoard();
            case "unified-todo" -> unifiedTodoBoard(cases, rowLimit);
            case "system-permission" -> systemPermissionBoard(rowLimit);
            case "system-log" -> systemLogBoard(rowLimit);
            case "system-datasource" -> systemDatasourceBoard();
            default -> throw new BusinessException("暂不支持的业务台账模块");
        };
    }

    private List<CaseInfo> loadCases(String keyword) {
        LambdaQueryWrapper<CaseInfo> query = new LambdaQueryWrapper<CaseInfo>()
                .orderByDesc(CaseInfo::getUpdatedTime)
                .orderByDesc(CaseInfo::getId)
                .last("limit 200");
        if (hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(CaseInfo::getCaseTitle, keyword)
                    .or()
                    .like(CaseInfo::getCaseNo, keyword)
                    .or()
                    .like(CaseInfo::getEntrustOrgName, keyword));
        }
        return caseInfoMapper.selectList(query);
    }

    private LedgerBoardDto crmBoard(List<CaseInfo> cases, String status, int rowLimit) {
        if (cases.isEmpty()) {
            return new LedgerBoardDto(
                    "crm",
                    "CRM 客户台账",
                    "按委托单位聚合案件活跃度、紧急程度和最近跟进情况。",
                    "sample",
                    List.of("all", "active", "urgent", "stabilized"),
                    List.of(
                            new LedgerMetricDto("委托单位", "3", false),
                            new LedgerMetricDto("跟进中客户", "2", false),
                            new LedgerMetricDto("紧急案件", "1", true),
                            new LedgerMetricDto("累计委托", "6", false)
                    ),
                    List.of(
                            new LedgerRowDto("crm-sample-1", "上海市某法院", "综合业务部", "法院客户", "张主任", "跟进中", "委托案件 3 件，紧急 1 件",
                                    "案件推进活跃，近两天有节点流转", "优先跟进紧急案件并确认反馈窗口",
                                    LocalDateTime.now().minusHours(6), LocalDateTime.now().plusDays(2), List.of("法院", "重点客户"),
                                    List.of("最近委托：交通事故伤残等级鉴定", "当前承接部门：综合业务部", "建议补联系人、跟进记录和客户分级")),
                            new LedgerRowDto("crm-sample-2", "某保险公估公司", "司法鉴定一部", "机构客户", "李经理", "跟进中", "委托案件 2 件，紧急 0 件",
                                    "近期有新增委托，适合补续签信息", "同步合同与项目推进情况",
                                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5), List.of("机构", "续签中"),
                                    List.of("最近委托：房屋损失评估鉴定", "当前承接部门：司法鉴定一部", "建议补商务跟进和续签计划")),
                            new LedgerRowDto("crm-sample-3", "某律所", "司法鉴定二部", "律所客户", "王主管", "已沉淀", "委托案件 1 件，紧急 0 件",
                                    "当前没有活跃流转，可纳入沉淀客户池", "补客户画像后再做激活",
                                    LocalDateTime.now().minusDays(8), null, List.of("律所"),
                                    List.of("最近委托：医疗损害责任鉴定", "当前承接部门：司法鉴定二部", "建议补联系人信息和下次回访时间"))
                    ),
                    List.of("补客户联系人与跟进记录", "补客户分级与转化状态", "接入合同与项目联动")
            );
        }

        Map<String, CustomerAggregate> grouped = new LinkedHashMap<>();
        for (CaseInfo item : cases) {
            String orgName = fallback(item.getEntrustOrgName(), "未命名委托方");
            grouped.computeIfAbsent(orgName, key -> new CustomerAggregate(orgName))
                    .accept(item);
        }
        List<CustomerAggregate> aggregates = grouped.values().stream()
                .sorted(Comparator.comparing(CustomerAggregate::lastUpdated, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CustomerAggregate::activeCaseCount, Comparator.reverseOrder()))
                .toList();

        long activeCustomers = aggregates.stream().filter(item -> item.activeCaseCount() > 0).count();
        long urgentCases = aggregates.stream().mapToLong(CustomerAggregate::urgentCaseCount).sum();
        long totalCases = aggregates.stream().mapToLong(CustomerAggregate::caseCount).sum();

        List<CustomerAggregate> filteredAggregates = aggregates.stream()
                .filter(item -> crmMatchesStatus(item, status))
                .toList();

        List<LedgerRowDto> rows = filteredAggregates.stream()
                .limit(rowLimit)
                .map(item -> new LedgerRowDto(
                        "crm-" + item.orgName().hashCode(),
                        item.orgName(),
                        fallback(item.acceptDeptName(), "待分配承接部门"),
                        item.urgentCaseCount() > 0 ? "重点客户" : "常规客户",
                        fallback(item.ownerName(), "待指派负责人"),
                        item.activeCaseCount() > 0 ? "跟进中" : "已沉淀",
                        "委托案件 " + item.caseCount() + " 件，紧急 " + item.urgentCaseCount() + " 件",
                        item.activeCaseCount() > 0 ? "最近仍有案件推进" : "当前无活跃流转",
                        item.urgentCaseCount() > 0 ? "优先确认紧急案件节点和客户反馈" : "补联系人、跟进记录和客户分级",
                        item.lastUpdated(),
                        item.nearestDeadline(),
                        item.tags(),
                        List.of(
                                "承接部门：" + fallback(item.acceptDeptName(), "待补"),
                                "当前负责人：" + fallback(item.ownerName(), "待补"),
                                "活跃案件数：" + item.activeCaseCount()
                        )
                ))
                .toList();

        return new LedgerBoardDto(
                "crm",
                "CRM 客户台账",
                "按委托单位聚合案件活跃度、紧急程度和最近跟进情况。",
                "live",
                List.of("all", "active", "urgent", "stabilized"),
                List.of(
                        new LedgerMetricDto("委托单位", String.valueOf(aggregates.size()), false),
                        new LedgerMetricDto("跟进中客户", String.valueOf(activeCustomers), false),
                        new LedgerMetricDto("紧急案件", String.valueOf(urgentCases), true),
                        new LedgerMetricDto("累计委托", String.valueOf(totalCases), false)
                ),
                rows,
                List.of("补客户联系人与跟进记录", "补客户分级与转化状态", "接入合同与项目联动")
        );
    }

    private LedgerBoardDto contractBoard(List<CaseInfo> cases, String status, int rowLimit) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        List<CaseInfo> filteredSource = source.stream()
                .filter(item -> contractMatchesStatus(item, status))
                .toList();
        List<CaseInfo> sorted = filteredSource.stream()
                .sorted(Comparator.comparing(this::caseUpdatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(rowLimit)
                .toList();

        long reviewingCount = source.stream().filter(item -> List.of("DRAFT", "TO_ACCEPT", "ACCEPT_REVIEWING", "REVIEWING").contains(item.getCaseStatus())).count();
        long activeCount = source.stream().filter(item -> List.of("PROCESSING", "DOC_ISSUING").contains(item.getCaseStatus())).count();
        long closedCount = source.stream().filter(item -> List.of("COMPLETED", "ARCHIVED", "TERMINATED").contains(item.getCaseStatus())).count();

        return new LedgerBoardDto(
                "contract",
                "合同台账",
                "先复用案件与委托数据沉淀合同清单，后续再接正式合同编号、金额和审批链。",
                cases.isEmpty() ? "sample" : "live",
                List.of("all", "drafting", "reviewing", "active", "closed", "terminated"),
                List.of(
                        new LedgerMetricDto("合同清单", String.valueOf(source.size()), false),
                        new LedgerMetricDto("审批中", String.valueOf(reviewingCount), false),
                        new LedgerMetricDto("履约中", String.valueOf(activeCount), true),
                        new LedgerMetricDto("已收口", String.valueOf(closedCount), false)
                ),
                sorted.stream()
                        .map(item -> new LedgerRowDto(
                                "contract-" + item.getId(),
                                fallback(item.getCaseNo(), "HT-" + item.getId()),
                                fallback(item.getEntrustOrgName(), "待补合同相对方"),
                                fallback(item.getAcceptDeptName(), "待分配承接部门"),
                                fallback(item.getCurrentHandlerName(), fallback(item.getAcceptDeptName(), "待分配负责人")),
                                contractStatus(item.getCaseStatus()),
                                fallback(item.getCaseTitle(), "未命名合同事项"),
                                fallback(item.getCurrentNodeName(), "待进入审批/履约节点"),
                                isOverdue(item) ? "优先确认延期说明和节点责任" : "补正式合同编号、金额和签约字段",
                                caseUpdatedTime(item),
                                item.getDeadlineTime(),
                                contractTags(item),
                                List.of(
                                        "流程状态：" + statusName(item.getCaseStatus()),
                                        "委托单位：" + fallback(item.getEntrustOrgName(), "待补"),
                                        "当前办理人：" + fallback(item.getCurrentHandlerName(), "待补")
                                )
                        ))
                        .toList(),
                List.of("补正式合同编号与金额", "补审批节点与签约日期", "接入客户和项目关联")
        );
    }

    private LedgerBoardDto projectBoard(List<CaseInfo> cases, String status, int rowLimit) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        List<CaseInfo> filteredSource = source.stream()
                .filter(item -> projectMatchesStatus(item, status))
                .toList();
        List<CaseInfo> sorted = filteredSource.stream()
                .sorted(Comparator.comparing(this::caseUpdatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(rowLimit)
                .toList();

        long processing = source.stream().filter(item -> List.of("PROCESSING", "REVIEWING", "DOC_ISSUING").contains(item.getCaseStatus())).count();
        long overdue = source.stream().filter(this::isOverdue).count();
        long urgent = source.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count();

        return new LedgerBoardDto(
                "project",
                "项目台账",
                "按案件推进项目视角台账，跟踪当前环节、负责人、截止时间和预警状态。",
                cases.isEmpty() ? "sample" : "live",
                List.of("all", "processing", "warning", "closed", "urgent"),
                List.of(
                        new LedgerMetricDto("项目总数", String.valueOf(source.size()), false),
                        new LedgerMetricDto("推进中", String.valueOf(processing), true),
                        new LedgerMetricDto("已超期", String.valueOf(overdue), overdue > 0),
                        new LedgerMetricDto("紧急项目", String.valueOf(urgent), false)
                ),
                sorted.stream()
                        .map(item -> new LedgerRowDto(
                                "project-" + item.getId(),
                                fallback(item.getCaseTitle(), "未命名项目"),
                                fallback(item.getEntrustOrgName(), "待补委托单位"),
                                fallback(item.getCaseNo(), "待补项目编号"),
                                fallback(item.getCurrentHandlerName(), fallback(item.getAcceptDeptName(), "待分配负责人")),
                                projectStatus(item),
                                fallback(item.getCurrentNodeName(), fallback(item.getCurrentNodeCode(), "待进入流程节点")),
                                isOverdue(item) ? "截止时间已超出，需要补延期说明" : "当前节点正常推进",
                                isOverdue(item) ? "优先处理超期说明和节点责任" : "补项目编号、里程碑和费用联动",
                                caseUpdatedTime(item),
                                item.getDeadlineTime(),
                                projectTags(item),
                                List.of(
                                        "案件状态：" + statusName(item.getCaseStatus()),
                                        "当前节点：" + fallback(item.getCurrentNodeName(), fallback(item.getCurrentNodeCode(), "待补")),
                                        "承接部门：" + fallback(item.getAcceptDeptName(), "待补")
                                )
                        ))
                        .toList(),
                List.of("补项目编号与计划节点", "补项目预警与延期说明", "接入合同、费用和归档联动")
        );
    }

    private LedgerBoardDto quickMailBoard() {
        return sampleBoard(
                "quick-mail", "电子邮件", "补齐快捷邮件入口、常用收件人和近期收发摘要。",
                List.of(new LedgerMetricDto("今日邮件", "8", false), new LedgerMetricDto("待处理", "3", true), new LedgerMetricDto("草稿", "2", false), new LedgerMetricDto("抄送", "5", false)),
                List.of(
                        row("mail-1", "委托单位补材确认", "发件人：综合业务部", "邮件草稿", "张主任", "待处理", "等待补材邮件发送",
                                "需在今日内发出并抄送项目负责人", "补模板、签名和附件联动", nowMinusHours(2), nowPlusHours(5), List.of("补材", "外发"), List.of("收件人待确认", "附件：补材清单", "建议接入案件模板")),
                        row("mail-2", "出庭材料确认", "发件人：项目辅助人", "法院沟通", "李经理", "进行中", "待法院回复回执",
                                "保持邮件链完整以便归档", "接入案件归档与回执留痕", nowMinusHours(6), nowPlusDays(1), List.of("法院", "回执"), List.of("已发送并抄送部门负责人", "需要补送达回执", "建议与知识库联动"))
                ),
                List.of("补邮箱账号配置", "接入案件模板和附件联动", "补送达回执和归档留痕")
        );
    }

    private LedgerBoardDto quickCalendarBoard() {
        return sampleBoard(
                "quick-calendar", "日程", "承接会议、出庭、回访和节点提醒等个人日程。",
                List.of(new LedgerMetricDto("今日事项", "6", false), new LedgerMetricDto("即将到期", "2", true), new LedgerMetricDto("本周会议", "4", false), new LedgerMetricDto("待确认", "1", false)),
                List.of(
                        row("calendar-1", "法院出庭准备", "关联项目：交通事故伤残等级鉴定", "今日 15:00", "王主管", "待处理", "需补出庭材料清单",
                                "出庭前确认调档与路线", "后续接项目节点提醒", nowMinusHours(1), nowPlusHours(4), List.of("出庭", "提醒"), List.of("关联案件号：沪司鉴 2026-001", "需带纸质档案", "建议与出庭通知联动")),
                        row("calendar-2", "重点客户回访", "对象：上海市某法院", "明日 10:00", "张主任", "进行中", "等待回访纪要",
                                "回访完成后记录客户反馈", "接入 CRM 跟进记录", nowMinusHours(8), nowPlusDays(1), List.of("客户", "回访"), List.of("需确认下批委托计划", "建议形成回访纪要", "后续可联动客户分级"))
                ),
                List.of("补个人/部门视图切换", "补日程提醒和同步", "接入案件与 CRM 事件联动")
        );
    }

    private LedgerBoardDto personalMessageBoard() {
        return sampleBoard(
                "personal-message", "消息", "汇总系统通知、流程提醒、风险预警和业务广播。",
                List.of(new LedgerMetricDto("未读消息", "5", true), new LedgerMetricDto("流程提醒", "3", false), new LedgerMetricDto("系统广播", "1", false), new LedgerMetricDto("风险预警", "1", true)),
                List.of(
                        row("message-1", "交通事故鉴定已临近截止", "来源：项目台账", "超期预警", "系统", "待处理", "项目还有 4 小时到期",
                                "优先查看项目详情并补延期说明", "补统一待办和消息已读状态", nowMinusHours(2), nowPlusHours(4), List.of("预警", "项目"), List.of("案件号：沪司鉴 2026-001", "当前环节：现场勘验", "建议通知项目负责人")),
                        row("message-2", "新角色权限已发布", "来源：系统管理", "配置通知", "管理员", "已送达", "权限菜单变更完成",
                                "建议同步检查影响范围", "补已读回执和订阅规则", nowMinusDays(1), null, List.of("系统", "权限"), List.of("涉及模块：系统管理", "变更范围：角色菜单", "建议补审计详情"))
                ),
                List.of("补已读/未读状态", "补消息订阅规则", "接入统一待办")
        );
    }

    private LedgerBoardDto personalTaskBoard(List<CaseInfo> cases, int rowLimit) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        List<CaseInfo> sorted = source.stream()
                .sorted(Comparator.comparing(this::caseUpdatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(rowLimit)
                .toList();
        long urgent = source.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count();
        long warning = source.stream().filter(this::isOverdue).count();
        return new LedgerBoardDto(
                "personal-task",
                "任务",
                "从现有案件与节点派生当前个人任务视图，后续接统一待办。",
                cases.isEmpty() ? "sample" : "live",
                List.of("all"),
                List.of(
                        new LedgerMetricDto("当前任务", String.valueOf(source.size()), false),
                        new LedgerMetricDto("紧急任务", String.valueOf(urgent), true),
                        new LedgerMetricDto("预警任务", String.valueOf(warning), warning > 0),
                        new LedgerMetricDto("本周更新", String.valueOf(sorted.size()), false)
                ),
                sorted.stream().map(item -> row(
                        "personal-task-" + item.getId(),
                        fallback(item.getCaseTitle(), "未命名任务"),
                        fallback(item.getCurrentNodeName(), "待进入节点"),
                        fallback(item.getCaseNo(), "待补编号"),
                        fallback(item.getCurrentHandlerName(), "待分配"),
                        projectStatus(item),
                        fallback(item.getCurrentNodeName(), fallback(item.getCurrentNodeCode(), "待处理")),
                        isOverdue(item) ? "需要立即补处理意见" : "按节点推进即可",
                        "后续接统一待办与提醒",
                        caseUpdatedTime(item),
                        item.getDeadlineTime(),
                        projectTags(item),
                        List.of("委托单位：" + fallback(item.getEntrustOrgName(), "待补"), "部门：" + fallback(item.getAcceptDeptName(), "待补"))
                )).toList(),
                List.of("接统一待办", "补认领/转办视图", "补任务已读与提醒")
        );
    }

    private LedgerBoardDto personalLogBoard() {
        return sampleBoard(
                "personal-log", "工作日志", "承接个人日报、项目纪要和出庭记录。",
                List.of(new LedgerMetricDto("本周日志", "4", false), new LedgerMetricDto("待补纪要", "2", true), new LedgerMetricDto("项目日志", "3", false), new LedgerMetricDto("共享日志", "1", false)),
                List.of(
                        row("log-1", "现场勘验纪要", "关联项目：交通事故伤残等级鉴定", "待补附件", "项目辅助人", "待处理", "需上传照片和设备记录",
                                "当天内补齐最稳妥", "后续接知识库留档", nowMinusHours(5), nowPlusHours(6), List.of("纪要", "附件"), List.of("建议补图片附件", "建议同步到知识库", "可联动现场勘验节点")),
                        row("log-2", "客户回访记录", "对象：某保险公估公司", "已保存草稿", "李经理", "进行中", "待补客户反馈结论",
                                "补客户评分后可进入 CRM", "后续接 CRM 客户画像", nowMinusDays(1), null, List.of("客户", "回访"), List.of("建议补回访结果", "建议补下次跟进时间", "可联动客户分级"))
                ),
                List.of("补日志模板", "接入项目/客户联动", "补共享与评论能力")
        );
    }

    private LedgerBoardDto personalAddressBoard() {
        return sampleBoard(
                "personal-address", "通讯簿", "承接法院、委托单位、内部同事和合作机构联系人。",
                List.of(new LedgerMetricDto("联系人", "28", false), new LedgerMetricDto("重点联系人", "6", true), new LedgerMetricDto("法院联系人", "9", false), new LedgerMetricDto("本周更新", "3", false)),
                List.of(
                        row("addr-1", "上海市某法院业务庭", "外部联系人", "法院", "张法官", "常用", "近期有出庭通知往来",
                                "建议补手机号与邮寄地址", "后续接 CRM 与法院函件流程", nowMinusDays(2), null, List.of("法院", "重点"), List.of("最近关联：出庭通知", "建议补送达地址", "可联动法院函件记录")),
                        row("addr-2", "综合业务部", "内部通讯组", "部门组", "部门负责人", "常用", "案件受理与分派高频使用",
                                "建议补岗位标签", "后续接组织架构同步", nowMinusDays(5), null, List.of("内部", "部门"), List.of("可同步组织架构", "建议补岗位标签", "可联动流程候选人"))
                ),
                List.of("补分组和搜索", "接入组织同步", "接入客户和法院联系人")
        );
    }

    private LedgerBoardDto applicationMineBoard() {
        int menuCount = sysMenuMapper == null ? 12 : Math.toIntExact(sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getDeleted, 0)));
        return sampleBoard(
                "application-mine", "我的应用", "按角色整理当前可用应用入口和常用组合。",
                List.of(new LedgerMetricDto("应用入口", String.valueOf(menuCount), false), new LedgerMetricDto("常用应用", "6", false), new LedgerMetricDto("待配置", "2", true), new LedgerMetricDto("第五阶段", "进行中", false)),
                List.of(
                        row("app-mine-1", "司法鉴定流程", "流程中心", "高频应用", "系统", "可用", "已可直接进入新建工作、我的工作和工作查询",
                                "建议补收藏和最近使用", "后续接应用分组与权限", nowMinusDays(1), null, List.of("流程", "高频"), List.of("已接入动态菜单", "可联动常用应用", "可补最近访问")),
                        row("app-mine-2", "完整 OA 业务域", "业务管理", "第五阶段", "系统", "建设中", "CRM、合同、项目已开工",
                                "建议继续补仓库和风险", "后续接应用授权和发布", nowMinusHours(3), null, List.of("OA", "业务"), List.of("CRM/合同/项目已具备结构化页面", "后续补应用权限范围", "可联动应用设计"))
                ),
                List.of("补收藏和最近使用", "补应用分组", "接入角色可见范围")
        );
    }

    private LedgerBoardDto applicationDesignBoard() {
        return sampleBoard(
                "application-design", "设计应用", "承接应用配置、入口编排和角色可见范围。",
                List.of(new LedgerMetricDto("待设计应用", "5", false), new LedgerMetricDto("可复用模块", "3", false), new LedgerMetricDto("权限配置项", "8", true), new LedgerMetricDto("发布流程", "规划中", false)),
                List.of(
                        row("app-design-1", "CRM 应用视图", "业务管理", "应用设计", "产品配置", "建设中", "需要补客户分级和联系人入口",
                                "优先补第一批真实字段", "后续接应用发布与灰度", nowMinusHours(6), null, List.of("CRM", "设计"), List.of("已存在模块中心", "建议补快捷入口", "建议补角色可见范围")),
                        row("app-design-2", "合同应用视图", "业务管理", "应用设计", "产品配置", "建设中", "需要补合同编号、金额和详情页",
                                "优先补详情表单与权限动作", "后续接应用发布与审批", nowMinusDays(1), null, List.of("合同", "设计"), List.of("已存在台账列表", "建议补筛选项", "建议补导出模板"))
                ),
                List.of("补应用分组和组件库", "补角色可见范围", "接入发布流程")
        );
    }

    private LedgerBoardDto performanceBoard(List<CaseInfo> cases, int rowLimit) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        Map<String, Long> counts = source.stream()
                .collect(Collectors.groupingBy(item -> fallback(item.getCurrentHandlerName(), "待分配"), LinkedHashMap::new, Collectors.counting()));
        List<LedgerRowDto> rows = counts.entrySet().stream()
                .limit(rowLimit)
                .map(entry -> row(
                        "performance-" + entry.getKey().hashCode(),
                        entry.getKey(),
                        "当前承接案件",
                        "绩效视角",
                        entry.getKey(),
                        "进行中",
                        "承接案件 " + entry.getValue() + " 件",
                        "适合作为办理量与时效基线",
                        "后续接评分、权重和统计周期",
                        LocalDateTime.now().minusHours(2),
                        null,
                        List.of("办理量"),
                        List.of("建议补评分规则", "建议补办理时长统计", "建议接部门对比"))
                ).toList();
        return sampleBoard("performance", "绩效", "先以案件承接量和时效预警承接绩效基线。",
                List.of(new LedgerMetricDto("在办人员", String.valueOf(counts.size()), false), new LedgerMetricDto("紧急案件", String.valueOf(source.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count()), true),
                        new LedgerMetricDto("超期案件", String.valueOf(source.stream().filter(this::isOverdue).count()), true), new LedgerMetricDto("统计周期", "本周", false)),
                rows,
                List.of("补评分规则", "补部门/人员对比", "接入导出报表"));
    }

    private LedgerBoardDto warehouseBoard() {
        return sampleBoard("warehouse", "仓库", "先承接材料出入库、设备领用与归还台账。", List.of(
                new LedgerMetricDto("库存台账", "12", false), new LedgerMetricDto("待归还设备", "2", true), new LedgerMetricDto("待入库材料", "3", false), new LedgerMetricDto("异常记录", "1", true)),
                List.of(
                        row("warehouse-1", "现场勘验设备箱", "库位：A-03", "设备", "档案管理员", "待归还", "关联现场勘验任务",
                                "归还后需补状态拍照", "后续接材料接收与返还流程", nowMinusHours(5), nowPlusDays(1), List.of("设备", "借出"), List.of("领用人：项目辅助人", "建议补二维码", "建议接入归还签收")),
                        row("warehouse-2", "纸质卷宗材料", "库位：B-12", "档案", "档案管理员", "待入库", "等待中心档案确认",
                                "入库后同步归档结果", "后续接归档流程", nowMinusDays(1), nowPlusDays(2), List.of("卷宗", "归档"), List.of("关联案件：医疗损害责任鉴定", "建议补扫描件链接", "建议接入邮寄回执"))),
                List.of("补设备台账", "补出入库留痕", "接入材料与归档联动"));
    }

    private LedgerBoardDto riskBoard(List<CaseInfo> cases, int rowLimit) {
        List<CaseInfo> risky = (cases.isEmpty() ? sampleCases() : cases).stream()
                .filter(item -> isOverdue(item) || Objects.equals(item.getUrgentFlag(), 1))
                .limit(rowLimit)
                .toList();
        List<LedgerRowDto> rows = risky.stream().map(item -> row(
                "risk-" + item.getId(),
                fallback(item.getCaseTitle(), "未命名风险项"),
                fallback(item.getEntrustOrgName(), "待补委托方"),
                fallback(item.getCaseNo(), "待补编号"),
                fallback(item.getCurrentHandlerName(), "待分配"),
                isOverdue(item) ? "预警中" : "高优先级",
                fallback(item.getCurrentNodeName(), "待处理节点"),
                isOverdue(item) ? "截止已超时" : "紧急案件需优先跟踪",
                "补风险级别、处置记录和复盘",
                caseUpdatedTime(item),
                item.getDeadlineTime(),
                projectTags(item),
                List.of("案件状态：" + statusName(item.getCaseStatus()), "承接部门：" + fallback(item.getAcceptDeptName(), "待补"), "建议补风险处置记录"))
        ).toList();
        return sampleBoard("risk", "安全风险", "从紧急与超期案件派生风险看板，后续补正式风险台账。",
                List.of(new LedgerMetricDto("风险事项", String.valueOf(risky.size()), true), new LedgerMetricDto("超期", String.valueOf(risky.stream().filter(this::isOverdue).count()), true),
                        new LedgerMetricDto("紧急", String.valueOf(risky.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count()), true), new LedgerMetricDto("待处置", String.valueOf(risky.size()), false)),
                rows,
                List.of("补风险级别", "补处置记录", "接入复盘与审计"));
    }

    private LedgerBoardDto noticeBoard() { return simpleOfficeBoard("notice", "公告新闻", "承接通知公告、新闻发布与阅读跟踪。"); }
    private LedgerBoardDto meetingBoard() { return simpleOfficeBoard("meeting", "会议", "承接会议安排、签到、纪要和跟进事项。"); }
    private LedgerBoardDto assetBoard() { return simpleOfficeBoard("asset", "资产", "承接资产台账、领用、归还和盘点。"); }
    private LedgerBoardDto attendanceBoard() { return simpleOfficeBoard("attendance", "考勤", "承接排班、打卡异常、请假和出差登记。"); }
    private LedgerBoardDto officialDocBoard() { return simpleOfficeBoard("official-doc", "公文", "承接收文、发文、流转和归档。"); }
    private LedgerBoardDto communityBoard() { return simpleOfficeBoard("community", "交流园地", "承接内部论坛、经验分享和通知互动。"); }
    private LedgerBoardDto openApiBoard() { return simpleOfficeBoard("open-api", "外部系统集成", "承接接口清单、认证方式和回调状态。"); }
    private LedgerBoardDto ssoBoard() { return simpleOfficeBoard("sso", "SSO", "承接单点登录接入、映射规则和回调验证。"); }
    private LedgerBoardDto systemDatasourceBoard() { return simpleOfficeBoard("system-datasource", "系统数据源", "承接数据库、缓存、对象存储和外部源配置。"); }

    private LedgerBoardDto hrBoard(int rowLimit) {
        if (sysUserMapper == null) {
            return simpleOfficeBoard("hr", "人力资源", "承接人员台账、入离职、岗位和组织视图。");
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0).orderByDesc(SysUser::getUpdatedTime).last("limit " + rowLimit));
        long enabled = users.stream().filter(item -> "enabled".equals(item.getStatus())).count();
        long recentlyActive = users.stream().filter(item -> item.getLastLoginTime() != null && item.getLastLoginTime().isAfter(LocalDateTime.now().minusDays(7))).count();
        return new LedgerBoardDto("hr", "人力资源", "基于现有用户台账承接人员、岗位和状态视图。", "live", List.of("all"),
                List.of(new LedgerMetricDto("人员", String.valueOf(users.size()), false), new LedgerMetricDto("启用", String.valueOf(enabled), false),
                        new LedgerMetricDto("近 7 日活跃", String.valueOf(recentlyActive), true), new LedgerMetricDto("待治理", String.valueOf(users.stream().filter(item -> !"enabled".equals(item.getStatus())).count()), false)),
                users.stream().map(item -> row("hr-" + item.getId(), fallback(item.getRealName(), item.getUsername()), fallback(item.getEmail(), "未填写邮箱"), fallback(item.getMobile(), "未填写手机号"),
                        fallback(item.getUsername(), "账号"), "enabled".equals(item.getStatus()) ? "在岗" : fallback(item.getStatus(), "待治理"), "最近登录：" + formatRelative(item.getLastLoginTime()),
                        "建议补岗位、入离职和组织履历", "后续接考勤与权限", firstNonNull(item.getUpdatedTime(), item.getCreatedTime()), null, List.of(fallback(item.getStatus(), "未知状态")),
                        List.of("账号：" + fallback(item.getUsername(), "待补"), "邮箱：" + fallback(item.getEmail(), "待补"), "手机号：" + fallback(item.getMobile(), "待补")))).toList(),
                List.of("补岗位信息", "补入离职状态", "接入考勤与权限联动"));
    }

    private LedgerBoardDto archiveBoard(int rowLimit) {
        if (caseArchiveRecordMapper == null || knowledgeDocumentMapper == null) {
            return simpleOfficeBoard("archive", "档案", "承接案件档案、公文档案和中心入库结果。");
        }
        List<CaseArchiveRecord> archives = caseArchiveRecordMapper.selectList(new LambdaQueryWrapper<CaseArchiveRecord>().orderByDesc(CaseArchiveRecord::getArchivedTime).last("limit " + rowLimit));
        int docs = Math.toIntExact(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getDeleted, 0)));
        return new LedgerBoardDto("archive", "档案", "基于自动归档与知识文档承接档案台账。", "live", List.of("all"),
                List.of(new LedgerMetricDto("归档记录", String.valueOf(archives.size()), false), new LedgerMetricDto("知识文档", String.valueOf(docs), false),
                        new LedgerMetricDto("中心入库", String.valueOf(archives.stream().filter(item -> "archived".equals(item.getArchiveStatus())).count()), true), new LedgerMetricDto("待补详情", String.valueOf(archives.stream().filter(item -> !hasText(item.getArchiveSummary())).count()), false)),
                archives.stream().map(item -> row("archive-" + item.getId(), fallback(item.getCaseNo(), "待补案号"), fallback(item.getNodeName(), "归档节点"), fallback(item.getArchiveType(), "档案"),
                        fallback(String.valueOf(item.getArchivedBy()), "系统"), fallback(item.getArchiveStatus(), "待处理"), fallback(item.getArchiveSummary(), "已记录归档动作"),
                        "建议补电子地址与入库位置", "后续接中心入库确认", item.getArchivedTime(), null, List.of(fallback(item.getArchiveType(), "档案")),
                        List.of("节点：" + fallback(item.getNodeName(), "待补"), "任务ID：" + fallback(String.valueOf(item.getTaskId()), "待补"), "文档ID：" + fallback(String.valueOf(item.getDocumentId()), "待补")))).toList(),
                List.of("补电子归档地址", "补中心入库结果", "接入公文与项目档案"));
    }

    private LedgerBoardDto supervisionBoard(List<CaseInfo> cases, int rowLimit) {
        List<CaseInfo> source = (cases.isEmpty() ? sampleCases() : cases).stream().filter(this::isOverdue).limit(rowLimit).toList();
        List<LedgerRowDto> rows = source.stream().map(item -> row("supervision-" + item.getId(), fallback(item.getCaseTitle(), "未命名督办项"),
                fallback(item.getAcceptDeptName(), "待补部门"), fallback(item.getCaseNo(), "待补编号"), fallback(item.getCurrentHandlerName(), "待分配"),
                "待督办", fallback(item.getCurrentNodeName(), "待处理"), "已超期，需要督办跟进", "后续接督办闭环", caseUpdatedTime(item), item.getDeadlineTime(), List.of("超期", "督办"),
                List.of("委托单位：" + fallback(item.getEntrustOrgName(), "待补"), "案件状态：" + statusName(item.getCaseStatus()), "建议补督办反馈"))).toList();
        return sampleBoard("supervision", "督查督办", "先以超期案件承接督办事项。", List.of(new LedgerMetricDto("督办项", String.valueOf(source.size()), true), new LedgerMetricDto("今日新增", String.valueOf(source.size()), false), new LedgerMetricDto("待反馈", String.valueOf(source.size()), true), new LedgerMetricDto("闭环率", "0%", false)), rows, List.of("补督办状态", "补反馈闭环", "接入消息提醒"));
    }

    private LedgerBoardDto portalBoard(List<CaseInfo> cases) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        long processing = source.stream().filter(item -> List.of("PROCESSING", "REVIEWING", "DOC_ISSUING").contains(item.getCaseStatus())).count();
        return sampleBoard("portal", "智能门户", "承接首页门户、模块摘要和重点提醒。", List.of(new LedgerMetricDto("模块", "10+", false), new LedgerMetricDto("在办案件", String.valueOf(processing), true), new LedgerMetricDto("紧急", String.valueOf(source.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count()), true), new LedgerMetricDto("待归档", String.valueOf(source.stream().filter(item -> "ARCHIVED".equals(item.getCaseStatus())).count()), false)),
                List.of(
                        row("portal-1", "司法鉴定主线", "流程中心摘要", "门户卡片", "系统", "可用", "第三、四阶段已完成，进入联调", "门户需补可配置卡片", "后续接门户布局配置", nowMinusHours(4), null, List.of("主线"), List.of("当前在办案件：" + processing, "建议补角色化卡片", "建议接公告和消息")),
                        row("portal-2", "完整 OA 业务域", "第五阶段", "门户卡片", "系统", "建设中", "CRM/合同/项目已具备结构化页面", "继续补剩余业务域页面", "后续接个性化门户", nowMinusHours(2), null, List.of("OA"), List.of("建议补分角色门户", "建议补常用入口", "建议接统计报表"))),
                List.of("补门户布局配置", "补角色化卡片", "接入公告、消息和报表"));
    }

    private LedgerBoardDto reportCenterBoard(List<CaseInfo> cases) {
        List<CaseInfo> source = cases.isEmpty() ? sampleCases() : cases;
        return sampleBoard("report-center", "报表中心", "先承接案件状态、紧急度、归档与时效统计报表。", List.of(
                new LedgerMetricDto("案件总量", String.valueOf(source.size()), false), new LedgerMetricDto("办理中", String.valueOf(source.stream().filter(item -> "PROCESSING".equals(item.getCaseStatus())).count()), true),
                new LedgerMetricDto("已办结", String.valueOf(source.stream().filter(item -> "COMPLETED".equals(item.getCaseStatus())).count()), false), new LedgerMetricDto("超期", String.valueOf(source.stream().filter(this::isOverdue).count()), true)),
                List.of(
                        row("report-1", "案件状态报表", "来源：case_info", "基础报表", "系统", "可用", "可按状态统计案件数量", "建议补导出和筛选条件", "后续接报表配置", nowMinusDays(1), null, List.of("统计"), List.of("建议按部门分组", "建议按时间趋势统计", "建议接 Excel 导出")),
                        row("report-2", "时效预警报表", "来源：case_info", "基础报表", "系统", "可用", "可识别超期和紧急案件", "建议补责任人和节点维度", "后续接图表组件", nowMinusHours(8), null, List.of("预警"), List.of("建议按节点分析", "建议按责任人分析", "建议接门户展示"))),
                List.of("补筛选和导出", "补图表视图", "接入权限与订阅"));
    }

    private LedgerBoardDto unifiedTodoBoard(List<CaseInfo> cases, int rowLimit) {
        return personalTaskBoard(cases, rowLimit);
    }

    private LedgerBoardDto systemPermissionBoard(int rowLimit) {
        if (sysRoleMapper == null || sysMenuMapper == null) {
            return simpleOfficeBoard("system-permission", "权限管理", "承接角色、菜单、数据范围和授权矩阵。");
        }
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0).orderByDesc(SysRole::getUpdatedTime).last("limit " + rowLimit));
        long menus = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getDeleted, 0));
        return new LedgerBoardDto("system-permission", "权限管理", "基于当前角色和菜单台账承接授权治理。", "live", List.of("all"),
                List.of(new LedgerMetricDto("角色", String.valueOf(roles.size()), false), new LedgerMetricDto("菜单", String.valueOf(menus), false), new LedgerMetricDto("启用角色", String.valueOf(roles.stream().filter(item -> "enabled".equals(item.getStatus())).count()), true), new LedgerMetricDto("数据范围", "5 类", false)),
                roles.stream().map(item -> row("role-" + item.getId(), fallback(item.getRoleName(), "未命名角色"), fallback(item.getRoleCode(), "待补编码"), fallback(item.getDataScope(), "待补范围"),
                        fallback(item.getRoleCode(), "角色"), "enabled".equals(item.getStatus()) ? "启用" : fallback(item.getStatus(), "待治理"), "数据范围：" + fallback(item.getDataScope(), "待补"),
                        "建议补角色矩阵与成员明细", "后续接权限变更审批", firstNonNull(item.getUpdatedTime(), item.getCreatedTime()), null, List.of("角色"), List.of("角色编码：" + fallback(item.getRoleCode(), "待补"), "数据范围：" + fallback(item.getDataScope(), "待补"), "建议补成员列表"))).toList(),
                List.of("补角色矩阵", "补成员明细", "接入权限变更审批"));
    }

    private LedgerBoardDto systemLogBoard(int rowLimit) {
        if (auditEventMapper == null) {
            return simpleOfficeBoard("system-log", "管理日志", "承接登录、下载、权限变更和业务审计查询。");
        }
        List<AuditEvent> events = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getOperatedTime).last("limit " + rowLimit));
        return new LedgerBoardDto("system-log", "管理日志", "基于审计事件承接系统与业务操作日志。", "live", List.of("all"),
                List.of(new LedgerMetricDto("最近日志", String.valueOf(events.size()), false), new LedgerMetricDto("成功", String.valueOf(events.stream().filter(item -> "success".equalsIgnoreCase(item.getResultStatus())).count()), false), new LedgerMetricDto("失败", String.valueOf(events.stream().filter(item -> !"success".equalsIgnoreCase(item.getResultStatus())).count()), true), new LedgerMetricDto("审计覆盖", "已开启", false)),
                events.stream().map(item -> row("audit-" + item.getId(), fallback(item.getActionName(), "未命名操作"), fallback(item.getOperatorName(), "系统"), fallback(item.getBizType(), "业务"),
                        fallback(item.getOperatorName(), "系统"), fallback(item.getResultStatus(), "unknown"), fallback(item.getActionCode(), "待补编码"),
                        hasText(item.getDetailJson()) ? "已记录详情" : "建议补更多审计细节", "后续接高级筛选与导出", item.getOperatedTime(), null, List.of(fallback(item.getBizType(), "审计")), List.of("业务ID：" + fallback(String.valueOf(item.getBizId()), "待补"), "案件ID：" + fallback(String.valueOf(item.getCaseId()), "待补"), "操作时间：" + formatRelative(item.getOperatedTime())))).toList(),
                List.of("补筛选条件", "补导出能力", "接入审计详情页"));
    }

    private boolean crmMatchesStatus(CustomerAggregate item, String status) {
        return switch (status) {
            case "active" -> item.activeCaseCount() > 0;
            case "urgent" -> item.urgentCaseCount() > 0;
            case "stabilized" -> item.activeCaseCount() == 0;
            default -> true;
        };
    }

    private boolean contractMatchesStatus(CaseInfo item, String status) {
        return switch (status) {
            case "drafting" -> List.of("DRAFT", "TO_ACCEPT").contains(item.getCaseStatus());
            case "reviewing" -> List.of("ACCEPT_REVIEWING", "REVIEWING").contains(item.getCaseStatus());
            case "active" -> List.of("PROCESSING", "DOC_ISSUING").contains(item.getCaseStatus());
            case "closed" -> List.of("COMPLETED", "ARCHIVED").contains(item.getCaseStatus());
            case "terminated" -> "TERMINATED".equals(item.getCaseStatus());
            default -> true;
        };
    }

    private boolean projectMatchesStatus(CaseInfo item, String status) {
        return switch (status) {
            case "processing" -> List.of("PROCESSING", "REVIEWING", "DOC_ISSUING").contains(item.getCaseStatus());
            case "warning" -> isOverdue(item);
            case "closed" -> List.of("COMPLETED", "ARCHIVED", "TERMINATED").contains(item.getCaseStatus());
            case "urgent" -> Objects.equals(item.getUrgentFlag(), 1);
            default -> true;
        };
    }

    private List<String> contractTags(CaseInfo item) {
        List<String> tags = new ArrayList<>();
        if (hasText(item.getCaseType())) {
            tags.add(item.getCaseType());
        }
        if (Objects.equals(item.getUrgentFlag(), 1)) {
            tags.add("紧急");
        }
        if (isOverdue(item)) {
            tags.add("超期");
        }
        return tags;
    }

    private List<String> projectTags(CaseInfo item) {
        List<String> tags = new ArrayList<>();
        if (hasText(item.getCaseStatus())) {
            tags.add(statusName(item.getCaseStatus()));
        }
        if (Objects.equals(item.getUrgentFlag(), 1)) {
            tags.add("紧急");
        }
        if (isOverdue(item)) {
            tags.add("超期");
        }
        return tags;
    }

    private String contractStatus(String status) {
        return switch (String.valueOf(status)) {
            case "DRAFT", "TO_ACCEPT" -> "草拟中";
            case "ACCEPT_REVIEWING", "REVIEWING" -> "审批中";
            case "PROCESSING", "DOC_ISSUING" -> "履约中";
            case "COMPLETED", "ARCHIVED" -> "已完成";
            case "TERMINATED" -> "已终止";
            default -> "待处理";
        };
    }

    private String projectStatus(CaseInfo item) {
        if (isOverdue(item)) {
            return "预警中";
        }
        return statusName(item.getCaseStatus());
    }

    private String statusName(String status) {
        return switch (String.valueOf(status)) {
            case "DRAFT" -> "草稿";
            case "TO_ACCEPT" -> "待受理";
            case "ACCEPT_REVIEWING" -> "受理审核中";
            case "REJECTED_ACCEPTANCE" -> "受理退回";
            case "CORRECTION_PENDING" -> "补正中";
            case "PROCESSING" -> "办理中";
            case "REVIEWING" -> "审核中";
            case "DOC_ISSUING" -> "文书出具中";
            case "COMPLETED" -> "已办结";
            case "ARCHIVED" -> "待归档";
            case "TERMINATED" -> "已终止";
            default -> "待处理";
        };
    }

    private LedgerBoardDto simpleOfficeBoard(String code, String name, String description) {
        return sampleBoard(
                code,
                name,
                description,
                List.of(
                        new LedgerMetricDto("本周事项", "4", false),
                        new LedgerMetricDto("待处理", "2", true),
                        new LedgerMetricDto("已发布", "1", false),
                        new LedgerMetricDto("待补规则", "1", false)
                ),
                List.of(
                        row(code + "-1", name + "主清单", "当前模块", "结构化页面", "系统", "建设中", "已从占位目录升级为可用页面",
                                "建议继续补真实字段和动作", "后续接审批、导出和审计", nowMinusHours(3), null, List.of("第五阶段"), List.of("已接统一页面框架", "建议补筛选和导出", "建议补详情页或办理动作")),
                        row(code + "-2", name + "后续细化", "当前模块", "待扩展", "系统", "规划中", "适合继续补字段、流程和明细",
                                "优先补高频动作和关键状态", "后续接权限和消息提醒", nowMinusDays(1), null, List.of("规划"), List.of("建议补详情视图", "建议补角色权限", "建议补统计口径")))
                ,
                List.of("补真实字段", "补详情与导出", "接入权限、流程和审计")
        );
    }

    private LedgerBoardDto sampleBoard(String code, String name, String description,
                                       List<LedgerMetricDto> metrics,
                                       List<LedgerRowDto> rows,
                                       List<String> nextActions) {
        return new LedgerBoardDto(code, name, description, "sample", List.of("all"), metrics, rows, nextActions);
    }

    private LedgerRowDto row(String rowKey,
                             String primaryText,
                             String secondaryText,
                             String tertiaryText,
                             String ownerName,
                             String statusLabel,
                             String metricText,
                             String progressLabel,
                             String actionHint,
                             LocalDateTime updatedTime,
                             LocalDateTime deadlineTime,
                             List<String> tags,
                             List<String> facts) {
        return new LedgerRowDto(rowKey, primaryText, secondaryText, tertiaryText, ownerName, statusLabel, metricText,
                progressLabel, actionHint, updatedTime, deadlineTime, tags, facts);
    }

    private LocalDateTime caseUpdatedTime(CaseInfo item) {
        return firstNonNull(item.getUpdatedTime(), item.getSubmittedTime(), item.getCreatedTime());
    }

    private boolean isOverdue(CaseInfo item) {
        return item.getDeadlineTime() != null && item.getDeadlineTime().isBefore(LocalDateTime.now())
                && !List.of("COMPLETED", "ARCHIVED", "TERMINATED").contains(item.getCaseStatus());
    }

    private String normalizeModuleCode(String moduleCode) {
        return fallback(moduleCode, "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean rowOverdue(LedgerRowDto row) {
        return row.deadlineTime() != null && row.deadlineTime().isBefore(LocalDateTime.now());
    }

    private LocalDateTime nowMinusHours(long hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    private LocalDateTime nowPlusHours(long hours) {
        return LocalDateTime.now().plusHours(hours);
    }

    private LocalDateTime nowPlusDays(long days) {
        return LocalDateTime.now().plusDays(days);
    }

    private LocalDateTime nowMinusDays(long days) {
        return LocalDateTime.now().minusDays(days);
    }

    private String formatRelative(LocalDateTime value) {
        if (value == null) {
            return "未记录";
        }
        return value.toString();
    }

    private String fallback(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<CaseInfo> sampleCases() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                sampleCase(101L, "沪司鉴 2026-001", "交通事故伤残等级鉴定", "交通事故", "PROCESSING", "现场勘验", "张主任",
                        "综合业务部", "上海市某法院", now.plusDays(2), 1, now.minusHours(8)),
                sampleCase(102L, "沪司鉴 2026-002", "房屋损失评估鉴定", "房屋损失", "REVIEWING", "项目负责人审核", "李经理",
                        "司法鉴定一部", "某保险公估公司", now.plusDays(4), 0, now.minusDays(1)),
                sampleCase(103L, "沪司鉴 2026-003", "医疗损害责任鉴定", "医疗损害", "COMPLETED", "归档", "王主管",
                        "司法鉴定二部", "某律所", now.minusDays(2), 0, now.minusDays(3))
        );
    }

    private CaseInfo sampleCase(Long id, String caseNo, String title, String caseType, String status, String nodeName,
                                String handlerName, String deptName, String entrustOrg, LocalDateTime deadlineTime,
                                Integer urgentFlag, LocalDateTime updatedTime) {
        CaseInfo item = new CaseInfo();
        item.setId(id);
        item.setCaseNo(caseNo);
        item.setCaseTitle(title);
        item.setCaseType(caseType);
        item.setCaseStatus(status);
        item.setCurrentNodeName(nodeName);
        item.setCurrentHandlerName(handlerName);
        item.setAcceptDeptName(deptName);
        item.setEntrustOrgName(entrustOrg);
        item.setDeadlineTime(deadlineTime);
        item.setUrgentFlag(urgentFlag);
        item.setUpdatedTime(updatedTime);
        item.setCreatedTime(updatedTime.minusDays(2));
        return item;
    }

    private static final class CustomerAggregate {
        private final String orgName;
        private int caseCount;
        private int activeCaseCount;
        private int urgentCaseCount;
        private String acceptDeptName;
        private String ownerName;
        private LocalDateTime lastUpdated;
        private LocalDateTime nearestDeadline;
        private final List<String> tagBuffer = new ArrayList<>();

        private CustomerAggregate(String orgName) {
            this.orgName = orgName;
        }

        private void accept(CaseInfo item) {
            caseCount++;
            if (!List.of("COMPLETED", "ARCHIVED", "TERMINATED").contains(item.getCaseStatus())) {
                activeCaseCount++;
            }
            if (Objects.equals(item.getUrgentFlag(), 1)) {
                urgentCaseCount++;
            }
            if (acceptDeptName == null && item.getAcceptDeptName() != null) {
                acceptDeptName = item.getAcceptDeptName();
            }
            if (ownerName == null && item.getCurrentHandlerName() != null) {
                ownerName = item.getCurrentHandlerName();
            }
            LocalDateTime updated = item.getUpdatedTime() != null ? item.getUpdatedTime() : item.getCreatedTime();
            if (updated != null && (lastUpdated == null || updated.isAfter(lastUpdated))) {
                lastUpdated = updated;
            }
            if (item.getDeadlineTime() != null && (nearestDeadline == null || item.getDeadlineTime().isBefore(nearestDeadline))) {
                nearestDeadline = item.getDeadlineTime();
            }
            if (item.getCaseType() != null) {
                tagBuffer.add(item.getCaseType());
            }
        }

        private String orgName() {
            return orgName;
        }

        private int caseCount() {
            return caseCount;
        }

        private int activeCaseCount() {
            return activeCaseCount;
        }

        private int urgentCaseCount() {
            return urgentCaseCount;
        }

        private String acceptDeptName() {
            return acceptDeptName;
        }

        private String ownerName() {
            return ownerName;
        }

        private LocalDateTime lastUpdated() {
            return lastUpdated;
        }

        private LocalDateTime nearestDeadline() {
            return nearestDeadline;
        }

        private List<String> tags() {
            return tagBuffer.stream().filter(Objects::nonNull).distinct().limit(3).collect(Collectors.toList());
        }
    }
}
