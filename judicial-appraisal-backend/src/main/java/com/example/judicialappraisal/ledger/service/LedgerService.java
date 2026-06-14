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
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MinioClient minioClient;
    private final Environment environment;

    public LedgerService(CaseInfoMapper caseInfoMapper) {
        this(caseInfoMapper, null, null, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public LedgerService(CaseInfoMapper caseInfoMapper,
                         SysUserMapper sysUserMapper,
                         SysRoleMapper sysRoleMapper,
                         SysMenuMapper sysMenuMapper,
                         AuditEventMapper auditEventMapper,
                         CaseArchiveRecordMapper caseArchiveRecordMapper,
                         KnowledgeDocumentMapper knowledgeDocumentMapper,
                         DataSource dataSource,
                         RedisConnectionFactory redisConnectionFactory,
                         MinioClient minioClient,
                         Environment environment) {
        this.caseInfoMapper = caseInfoMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.auditEventMapper = auditEventMapper;
        this.caseArchiveRecordMapper = caseArchiveRecordMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.minioClient = minioClient;
        this.environment = environment;
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
            case "warehouse" -> warehouseBoard(normalizedStatus);
            case "risk" -> riskBoard(cases, normalizedStatus, rowLimit);
            case "notice" -> noticeBoard(normalizedStatus, rowLimit);
            case "meeting" -> meetingBoard(normalizedStatus, rowLimit);
            case "asset" -> assetBoard(normalizedStatus, rowLimit);
            case "hr" -> hrBoard(rowLimit);
            case "attendance" -> attendanceBoard(normalizedStatus, rowLimit);
            case "official-doc" -> officialDocBoard(normalizedStatus, rowLimit);
            case "archive" -> archiveBoard(normalizedStatus, rowLimit);
            case "community" -> communityBoard(normalizedStatus, rowLimit);
            case "supervision" -> supervisionBoard(cases, rowLimit);
            case "portal" -> portalBoard(cases);
            case "report-center" -> reportCenterBoard(cases);
            case "open-api" -> openApiBoard(normalizedStatus, rowLimit);
            case "sso" -> ssoBoard(normalizedStatus, rowLimit);
            case "unified-todo" -> unifiedTodoBoard(cases, rowLimit);
            case "system-permission" -> systemPermissionBoard(normalizedStatus, rowLimit);
            case "system-log" -> systemLogBoard(normalizedStatus, rowLimit);
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
                                    List.of("最近委托：交通事故伤残等级鉴定", "当前承接部门：综合业务部", "建议补联系人、跟进记录和客户分级"), "/case/101"),
                            new LedgerRowDto("crm-sample-2", "某保险公估公司", "司法鉴定一部", "机构客户", "李经理", "跟进中", "委托案件 2 件，紧急 0 件",
                                    "近期有新增委托，适合补续签信息", "同步合同与项目推进情况",
                                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5), List.of("机构", "续签中"),
                                    List.of("最近委托：房屋损失评估鉴定", "当前承接部门：司法鉴定一部", "建议补商务跟进和续签计划"), "/case/102"),
                            new LedgerRowDto("crm-sample-3", "某律所", "司法鉴定二部", "律所客户", "王主管", "已沉淀", "委托案件 1 件，紧急 0 件",
                                    "当前没有活跃流转，可纳入沉淀客户池", "补客户画像后再做激活",
                                    LocalDateTime.now().minusDays(8), null, List.of("律所"),
                                    List.of("最近委托：医疗损害责任鉴定", "当前承接部门：司法鉴定二部", "建议补联系人信息和下次回访时间"), "/case/103")
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
                                "活跃案件数：" + item.activeCaseCount(),
                                "最近委托：" + fallback(item.latestCaseTitle(), "待补"),
                                "客户分级：" + customerTier(item),
                                "建议跟进时间：" + formatRelative(nextCustomerFollowUp(item))
                        ),
                        casePath(item.latestCaseId())
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
                                        "案件编号：" + fallback(item.getCaseNo(), "待补"),
                                        "合同编号草案：" + contractDraftNumber(item),
                                        "流程状态：" + statusName(item.getCaseStatus()),
                                        "委托单位：" + fallback(item.getEntrustOrgName(), "待补"),
                                        "当前办理人：" + fallback(item.getCurrentHandlerName(), "待补"),
                                        "签约窗口建议：" + contractSigningWindow(item),
                                        "截止时间：" + formatRelative(item.getDeadlineTime())
                                ),
                                casePath(item)
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
                                        "案件编号：" + fallback(item.getCaseNo(), "待补"),
                                        "项目里程碑：" + projectMilestone(item),
                                        "案件状态：" + statusName(item.getCaseStatus()),
                                        "当前节点：" + fallback(item.getCurrentNodeName(), fallback(item.getCurrentNodeCode(), "待补")),
                                        "承接部门：" + fallback(item.getAcceptDeptName(), "待补"),
                                        "下一检查点：" + projectCheckpoint(item),
                                        "截止时间：" + formatRelative(item.getDeadlineTime())
                                ),
                                casePath(item)
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

    private LedgerBoardDto warehouseBoard(String status) {
        List<LedgerRowDto> rows = List.of(
                row("warehouse-1", "现场勘验设备箱", "库位：A-03", "设备", "档案管理员", "待归还", "关联现场勘验任务",
                        "归还后需补状态拍照", "后续接材料接收与返还流程", nowMinusHours(5), nowPlusDays(1), List.of("设备", "借出"), List.of("领用人：项目辅助人", "建议补二维码", "建议接入归还签收")),
                row("warehouse-2", "纸质卷宗材料", "库位：B-12", "档案", "档案管理员", "待入库", "等待中心档案确认",
                        "入库后同步归档结果", "后续接归档流程", nowMinusDays(1), nowPlusDays(2), List.of("卷宗", "归档"), List.of("关联案件：医疗损害责任鉴定", "建议补扫描件链接", "建议接入邮寄回执")),
                row("warehouse-3", "移动硬盘证据材料", "库位：介质柜 M-02", "电子介质", "项目辅助人", "在库", "已完成介质登记",
                        "等待项目负责人确认下一步", "后续接材料返还和审计", nowMinusDays(2), null, List.of("介质", "在库"), List.of("介质编号：M-2026-018", "保管人：档案管理员", "建议接入介质借阅"))
        );
        return moduleWorkBoard("warehouse", "仓库", "承接材料出入库、设备领用、介质保管和归还台账。",
                List.of("all", "borrowed", "inbound", "stock"),
                List.of(
                new LedgerMetricDto("库存台账", "12", false), new LedgerMetricDto("待归还设备", "2", true), new LedgerMetricDto("待入库材料", "3", false), new LedgerMetricDto("异常记录", "1", true)),
                rows.stream().filter(row -> moduleStatusMatches("warehouse", row, status)).toList(),
                List.of("补设备台账", "补出入库留痕", "接入材料与归档联动"));
    }

    private LedgerBoardDto riskBoard(List<CaseInfo> cases, String status, int rowLimit) {
        List<CaseInfo> risky = (cases.isEmpty() ? sampleCases() : cases).stream()
                .filter(item -> isOverdue(item) || Objects.equals(item.getUrgentFlag(), 1))
                .filter(item -> riskMatchesStatus(item, status))
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
        return new LedgerBoardDto("risk", "安全风险", "从紧急与超期案件派生风险看板，后续补正式风险台账。",
                cases.isEmpty() ? "sample" : "live",
                List.of("all", "overdue", "urgent"),
                List.of(new LedgerMetricDto("风险事项", String.valueOf(risky.size()), true), new LedgerMetricDto("超期", String.valueOf(risky.stream().filter(this::isOverdue).count()), true),
                        new LedgerMetricDto("紧急", String.valueOf(risky.stream().filter(item -> Objects.equals(item.getUrgentFlag(), 1)).count()), true), new LedgerMetricDto("待处置", String.valueOf(risky.size()), false)),
                rows,
                List.of("补风险级别", "补处置记录", "接入复盘与审计"));
    }

    private LedgerBoardDto noticeBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("notice-1", "五一值班与窗口安排", "行政办公", "通知公告", "综合业务部", "已发布", "阅读率 82%", "已同步首页门户", "补阅读回执和未读提醒", nowMinusHours(7), nowPlusDays(5), List.of("公告", "门户"), List.of("发布范围：全员", "需回执部门：综合业务部", "建议接入消息中心")),
                row("notice-2", "司法鉴定材料流转规范", "制度新闻", "制度草稿", "质量管理", "草稿", "待部门负责人审核", "已完成正文初稿", "补附件版本和发布范围", nowMinusDays(1), nowPlusDays(2), List.of("制度", "草稿"), List.of("附件：材料流转规范.docx", "发布对象：鉴定业务部门", "建议接知识库制度目录")),
                row("notice-3", "办公室安全检查通报", "行政办公", "新闻审核", "行政", "待审核", "待综合业务部确认", "图片和正文已补齐", "审核后同步门户", nowMinusHours(4), nowPlusDays(1), List.of("新闻", "审核"), List.of("关联风险：办公区域用电", "建议补审核意见", "可同步督查督办"))
        );
        return moduleWorkBoard("notice", "公告新闻", "承接通知公告、新闻发布、制度草稿与阅读跟踪。",
                List.of("all", "published", "draft", "pending"),
                List.of(new LedgerMetricDto("公告新闻", "18", false), new LedgerMetricDto("待审核", "3", true), new LedgerMetricDto("草稿", "4", false), new LedgerMetricDto("本周发布", "5", false)),
                rows.stream().filter(row -> moduleStatusMatches("notice", row, status)).limit(rowLimit).toList(),
                List.of("补发布审批", "补阅读回执", "接入门户和知识库"));
    }

    private LedgerBoardDto meetingBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("meeting-1", "司法鉴定周例会", "会议室 A", "部门会议", "综合业务部", "待召开", "12 人参会", "议题已收集", "会后补纪要和待办", nowMinusHours(2), nowPlusHours(6), List.of("会议", "待召开"), List.of("主持人：部门负责人", "议题：第四阶段联调验收", "建议接统一待办")),
                row("meeting-2", "质量控制复盘会", "线上会议", "专题会议", "质量管理", "待纪要", "纪要草稿未提交", "已完成签到", "补纪要和行动项负责人", nowMinusDays(1), nowPlusHours(10), List.of("质量", "纪要"), List.of("参会：项目负责人/技术负责人", "待办项：3 条", "建议同步知识库")),
                row("meeting-3", "行政资产盘点会", "会议室 B", "行政会议", "行政", "已完成", "纪要已归档", "行动项 2 条", "跟踪资产盘点闭环", nowMinusDays(3), null, List.of("行政", "完成"), List.of("关联资产：设备箱", "归档目录：行政会议", "建议同步督办"))
        );
        return moduleWorkBoard("meeting", "会议", "承接会议安排、签到、纪要和跟进事项。",
                List.of("all", "scheduled", "minutes", "done"),
                List.of(new LedgerMetricDto("本周会议", "9", false), new LedgerMetricDto("待召开", "4", true), new LedgerMetricDto("待纪要", "2", true), new LedgerMetricDto("行动项", "7", false)),
                rows.stream().filter(row -> moduleStatusMatches("meeting", row, status)).limit(rowLimit).toList(),
                List.of("补签到和纪要模板", "补行动项跟踪", "接入统一待办"));
    }

    private LedgerBoardDto assetBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("asset-1", "便携式扫描仪", "资产编号：ZC-2026-019", "办公设备", "行政", "在用", "领用人：档案管理员", "用于卷宗扫描", "补归还周期和维保记录", nowMinusDays(2), nowPlusDays(30), List.of("设备", "在用"), List.of("当前位置：档案室", "最近盘点：正常", "建议接入维修流程")),
                row("asset-2", "勘验相机", "资产编号：ZC-2026-006", "专业设备", "项目辅助人", "维修中", "镜头校准中", "预计 2 天后返还", "同步风险和仓库状态", nowMinusHours(8), nowPlusDays(2), List.of("维修", "勘验"), List.of("维修单：WX-2026-010", "影响流程：现场勘验", "建议提示替代设备")),
                row("asset-3", "备用显示器", "资产编号：ZC-2026-031", "办公设备", "行政", "闲置", "可调拨", "待新员工领用", "补调拨审批和领用记录", nowMinusDays(5), null, List.of("闲置", "可调拨"), List.of("库位：C-01", "状态：完好", "建议接入领用审批"))
        );
        return moduleWorkBoard("asset", "资产", "承接资产台账、领用、归还、维修和盘点。",
                List.of("all", "in-use", "idle", "maintenance"),
                List.of(new LedgerMetricDto("资产台账", "46", false), new LedgerMetricDto("在用", "31", false), new LedgerMetricDto("维修中", "2", true), new LedgerMetricDto("闲置可调拨", "6", false)),
                rows.stream().filter(row -> moduleStatusMatches("asset", row, status)).limit(rowLimit).toList(),
                List.of("补领用审批", "补维修闭环", "接入仓库与盘点"));
    }

    private LedgerBoardDto attendanceBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("attendance-1", "张主任", "综合业务部", "今日考勤", "张主任", "正常", "09:02 已打卡", "外勤审批已同步", "后续接移动打卡", nowMinusHours(6), null, List.of("正常", "外勤"), List.of("班次：行政班", "定位：办公区", "关联日程：客户回访")),
                row("attendance-2", "李经理", "司法鉴定一部", "异常打卡", "李经理", "异常", "缺少下班卡", "待本人补卡说明", "同步个人事务提醒", nowMinusHours(2), nowPlusHours(8), List.of("异常", "补卡"), List.of("异常类型：漏打卡", "处理人：部门负责人", "建议接补卡流程")),
                row("attendance-3", "王主管", "司法鉴定二部", "请假登记", "王主管", "请假", "年假 1 天", "已通过审批", "同步排班和任务代理", nowMinusDays(1), nowPlusDays(1), List.of("请假", "审批"), List.of("假别：年假", "代理人：项目负责人", "建议接工作委托"))
        );
        return moduleWorkBoard("attendance", "考勤", "承接排班、打卡异常、请假和出差登记。",
                List.of("all", "normal", "exception", "leave"),
                List.of(new LedgerMetricDto("今日出勤", "36", false), new LedgerMetricDto("异常", "3", true), new LedgerMetricDto("请假", "2", false), new LedgerMetricDto("待审批", "4", true)),
                rows.stream().filter(row -> moduleStatusMatches("attendance", row, status)).limit(rowLimit).toList(),
                List.of("补补卡流程", "补排班规则", "接入工作委托"));
    }

    private LedgerBoardDto officialDocBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("official-doc-1", "关于司法鉴定流程联调的通知", "发文拟稿", "发文", "综合业务部", "流转中", "待部门负责人核稿", "正文和附件已上传", "补套红和归档", nowMinusHours(5), nowPlusDays(1), List.of("发文", "核稿"), List.of("文号草案：司鉴办〔2026〕12号", "当前节点：部门负责人核稿", "建议接入用章流程")),
                row("official-doc-2", "法院来函办理单", "收文登记", "收文", "档案管理员", "已发布", "已分派项目负责人", "已关联法院函件流程", "跟踪回函期限", nowMinusDays(1), nowPlusDays(3), List.of("收文", "法院"), List.of("来文单位：上海市某法院", "关联流程：court-letter", "建议接案件详情")),
                row("official-doc-3", "行政制度修订稿", "制度文件", "公文草稿", "行政", "草稿", "待补修订说明", "正文已保存", "补审批链和发布范围", nowMinusDays(2), nowPlusDays(5), List.of("制度", "草稿"), List.of("发布范围：全员", "版本：V2", "建议接知识库制度目录"))
        );
        return moduleWorkBoard("official-doc", "公文", "承接收文、发文、流转、用章和归档。",
                List.of("all", "draft", "reviewing", "published"),
                List.of(new LedgerMetricDto("公文", "22", false), new LedgerMetricDto("流转中", "6", true), new LedgerMetricDto("草稿", "5", false), new LedgerMetricDto("已发布", "11", false)),
                rows.stream().filter(row -> moduleStatusMatches("official-doc", row, status)).limit(rowLimit).toList(),
                List.of("补发文/收文流程", "补套红和用章", "接入档案归档"));
    }

    private LedgerBoardDto communityBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("community-1", "现场勘验经验分享", "经验交流", "帖子", "项目辅助人", "热门", "阅读 128 次", "评论活跃", "沉淀到知识库最佳实践", nowMinusHours(9), null, List.of("经验", "热门"), List.of("关联模块：现场勘验", "评论：12 条", "建议转知识文档")),
                row("community-2", "新员工入职问答", "交流园地", "问答", "人资", "已分享", "已采纳 3 条", "持续更新中", "补常见问题标签", nowMinusDays(2), null, List.of("问答", "HR"), List.of("关联模块：人力资源", "采纳答案：3 条", "建议接门户推荐")),
                row("community-3", "费用报销注意事项", "财务交流", "待审核", "财务", "待审核", "待财务负责人确认", "内容已提交", "审核后同步知识库", nowMinusHours(4), nowPlusDays(1), List.of("财务", "审核"), List.of("关联流程：expense-reimbursement", "附件：报销样例", "建议加审核意见"))
        );
        return moduleWorkBoard("community", "交流园地", "承接内部论坛、经验分享、问答和通知互动。",
                List.of("all", "hot", "shared", "pending"),
                List.of(new LedgerMetricDto("帖子", "34", false), new LedgerMetricDto("热门", "5", true), new LedgerMetricDto("待审核", "3", true), new LedgerMetricDto("已沉淀", "8", false)),
                rows.stream().filter(row -> moduleStatusMatches("community", row, status)).limit(rowLimit).toList(),
                List.of("补审核规则", "补知识沉淀", "接入门户推荐"));
    }

    private LedgerBoardDto openApiBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("open-api-1", "统一待办推送接口", "POST /open/todo/push", "接口", "集成平台", "在线", "成功率 99.2%", "已接入鉴权", "补幂等键和重试监控", nowMinusHours(1), null, List.of("API", "在线"), List.of("认证：AK/SK", "回调：已配置", "建议接入调用日志")),
                row("open-api-2", "电子签章回调", "POST /open/seal/callback", "回调", "集成平台", "预警", "最近失败 2 次", "验签通过但业务状态缺失", "优先补失败重试和告警", nowMinusHours(3), nowPlusHours(4), List.of("回调", "预警"), List.of("关联流程：seal-application", "失败原因：缺少文件ID", "建议接系统日志")),
                row("open-api-3", "档案系统同步接口", "POST /open/archive/sync", "接口草稿", "档案管理员", "草稿", "字段映射待确认", "已完成数据项清单", "补验签和同步结果", nowMinusDays(1), nowPlusDays(3), List.of("档案", "草稿"), List.of("目标系统：中心档案", "映射字段：12 个", "建议补沙箱联调"))
        );
        return moduleWorkBoard("open-api", "外部系统集成", "承接接口清单、认证方式、回调状态和集成监控。",
                List.of("all", "online", "warning", "draft"),
                List.of(new LedgerMetricDto("接口", "14", false), new LedgerMetricDto("在线", "9", false), new LedgerMetricDto("预警", "2", true), new LedgerMetricDto("草稿", "3", false)),
                rows.stream().filter(row -> moduleStatusMatches("open-api", row, status)).limit(rowLimit).toList(),
                List.of("补接口认证", "补调用日志", "接入告警和重试"));
    }

    private LedgerBoardDto ssoBoard(String status, int rowLimit) {
        List<LedgerRowDto> rows = List.of(
                row("sso-1", "OA 管理后台", "OIDC Client", "单点登录", "系统管理员", "已启用", "最近登录 42 次", "角色映射正常", "补登出回调", nowMinusHours(2), null, List.of("OIDC", "启用"), List.of("ClientId：oa-admin", "映射角色：ADMIN", "建议补会话审计")),
                row("sso-2", "移动端入口", "SAML 应用", "单点登录", "集成平台", "待配置", "证书待上传", "元数据已生成", "补证书和回调地址", nowMinusDays(1), nowPlusDays(2), List.of("SAML", "配置"), List.of("回调地址：待确认", "证书：未上传", "建议接移动端联调")),
                row("sso-3", "档案中心", "CAS 接入", "单点登录", "系统管理员", "预警", "票据校验偶发失败", "等待第三方确认", "补失败日志和重试策略", nowMinusHours(6), nowPlusDays(1), List.of("CAS", "预警"), List.of("第三方：中心档案", "失败次数：2", "建议接系统日志"))
        );
        return moduleWorkBoard("sso", "SSO", "承接单点登录接入、账号映射、回调验证和会话审计。",
                List.of("all", "enabled", "pending", "warning"),
                List.of(new LedgerMetricDto("接入应用", "6", false), new LedgerMetricDto("已启用", "4", false), new LedgerMetricDto("待配置", "1", true), new LedgerMetricDto("预警", "1", true)),
                rows.stream().filter(row -> moduleStatusMatches("sso", row, status)).limit(rowLimit).toList(),
                List.of("补账号映射", "补登出回调", "接入会话审计"));
    }

    private LedgerBoardDto systemDatasourceBoard() {
        if (environment == null) {
            return simpleOfficeBoard("system-datasource", "系统数据源", "承接数据库、缓存、对象存储和外部源配置。");
        }
        String mysqlUrl = fallback(environment.getProperty("spring.datasource.url"), "未配置");
        String redisHost = fallback(environment.getProperty("spring.data.redis.host"), "未配置");
        String redisPort = fallback(environment.getProperty("spring.data.redis.port"), "未配置");
        String minioEndpoint = fallback(environment.getProperty("app.minio.endpoint"), "未配置");
        String bucket = fallback(environment.getProperty("app.minio.bucket"), "未配置");

        String mysqlStatus = checkMysqlStatus();
        String redisStatus = checkRedisStatus();
        String minioStatus = checkMinioStatus(bucket);

        return new LedgerBoardDto(
                "system-datasource",
                "系统数据源",
                "直接展示当前 MySQL、Redis、MinIO 配置摘要和连通性。",
                "live",
                List.of("all"),
                List.of(
                        new LedgerMetricDto("MySQL", mysqlStatus, !"可用".equals(mysqlStatus)),
                        new LedgerMetricDto("Redis", redisStatus, !"可用".equals(redisStatus)),
                        new LedgerMetricDto("MinIO", minioStatus, !"可用".equals(minioStatus)),
                        new LedgerMetricDto("Bucket", bucket, false)
                ),
                List.of(
                        row("datasource-mysql", "MySQL", mysqlUrl, fallback(environment.getProperty("spring.datasource.username"), "未配置用户"),
                                "系统", mysqlStatus, "主业务数据库连接", mysqlStatus.equals("可用") ? "当前配置可正常访问" : "请检查数据库服务和账号配置",
                                "后续可补慢查询和连接池指标", LocalDateTime.now(), null, List.of("数据库"), List.of("JDBC URL：" + mysqlUrl, "用户名：" + fallback(environment.getProperty("spring.datasource.username"), "未配置"), "状态：" + mysqlStatus)),
                        row("datasource-redis", "Redis", redisHost + ":" + redisPort, fallback(environment.getProperty("spring.data.redis.database"), "0"),
                                "系统", redisStatus, "缓存与会话存储", redisStatus.equals("可用") ? "当前连接正常" : "请检查 Redis 服务和端口",
                                "后续可补 key 统计与延迟", LocalDateTime.now(), null, List.of("缓存"), List.of("主机：" + redisHost, "端口：" + redisPort, "状态：" + redisStatus)),
                        row("datasource-minio", "MinIO", minioEndpoint, bucket,
                                "系统", minioStatus, "对象存储与文件预览", minioStatus.equals("可用") ? "当前 Bucket 可访问" : "请检查 Endpoint、凭证和 Bucket",
                                "后续可补容量与对象数量", LocalDateTime.now(), null, List.of("对象存储"), List.of("Endpoint：" + minioEndpoint, "Bucket：" + bucket, "状态：" + minioStatus))
                ),
                List.of("补连接池与容量指标", "补慢查询/延迟监控", "接入运维告警")
        );
    }

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

    private LedgerBoardDto archiveBoard(String status, int rowLimit) {
        if (caseArchiveRecordMapper == null || knowledgeDocumentMapper == null) {
            return simpleOfficeBoard("archive", "档案", "承接案件档案、公文档案和中心入库结果。");
        }
        List<CaseArchiveRecord> archives = caseArchiveRecordMapper.selectList(new LambdaQueryWrapper<CaseArchiveRecord>().orderByDesc(CaseArchiveRecord::getArchivedTime).last("limit 200"));
        int docs = Math.toIntExact(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getDeleted, 0)));
        List<CaseArchiveRecord> filtered = archives.stream()
                .filter(item -> archiveMatchesStatus(item, status))
                .limit(rowLimit)
                .toList();
        return new LedgerBoardDto("archive", "档案", "基于自动归档与知识文档承接档案台账。", "live",
                List.of("all", "archived", "pending"),
                List.of(new LedgerMetricDto("归档记录", String.valueOf(archives.size()), false), new LedgerMetricDto("知识文档", String.valueOf(docs), false),
                        new LedgerMetricDto("中心入库", String.valueOf(archives.stream().filter(item -> "archived".equals(item.getArchiveStatus())).count()), true), new LedgerMetricDto("待补详情", String.valueOf(archives.stream().filter(item -> !hasText(item.getArchiveSummary())).count()), false)),
                filtered.stream().map(item -> new LedgerRowDto(
                        "archive-" + item.getId(),
                        fallback(item.getCaseNo(), "待补案号"),
                        fallback(item.getNodeName(), "归档节点"),
                        fallback(item.getArchiveType(), "档案"),
                        fallback(String.valueOf(item.getArchivedBy()), "系统"),
                        archiveStatusLabel(item.getArchiveStatus()),
                        fallback(item.getArchiveSummary(), "已记录归档动作"),
                        hasText(item.getArchiveSummary()) ? "归档摘要已留痕" : "待补归档摘要与电子地址",
                        "建议补电子地址、入库位置和借阅状态",
                        item.getArchivedTime(),
                        null,
                        List.of(fallback(item.getArchiveType(), "档案"), archiveStatusLabel(item.getArchiveStatus())),
                        List.of(
                                "节点：" + fallback(item.getNodeName(), "待补"),
                                "任务ID：" + fallback(String.valueOf(item.getTaskId()), "待补"),
                                "文档ID：" + fallback(String.valueOf(item.getDocumentId()), "待补"),
                                "案件ID：" + fallback(String.valueOf(item.getCaseId()), "待补")
                        ),
                        "/knowledge"
                )).toList(),
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
        List<CaseInfo> source = cases.isEmpty() ? loadCases(null) : cases;
        
        long processingCount = source.stream().filter(item -> "PROCESSING".equals(item.getCaseStatus())).count();
        long completedCount = source.stream().filter(item -> "COMPLETED".equals(item.getCaseStatus())).count();
        long urgentCount = source.stream().filter(item -> Integer.valueOf(1).equals(item.getUrgentFlag())).count();
        long overdueCount = source.stream().filter(this::isOverdue).count();

        return new LedgerBoardDto(
                "report-center",
                "报表中心",
                "根据真实案件流转情况自动收集的统计信息，支持多维度业务数据分析。",
                "live",
                List.of("全部状态", "办理中", "已办结", "超期提醒"),
                List.of(
                        new LedgerMetricDto("案件总数", String.valueOf(source.size()), false),
                        new LedgerMetricDto("办理中", String.valueOf(processingCount), true),
                        new LedgerMetricDto("已办结", String.valueOf(completedCount), false),
                        new LedgerMetricDto("紧急案件", String.valueOf(urgentCount), true)
                ),
                source.stream().map(item -> new LedgerRowDto(
                        "case-" + item.getId(),
                        fallback(item.getCaseNo(), "待编案号"),
                        fallback(item.getCaseTitle(), "未命名案件"),
                        fallback(item.getCaseType(), "司法鉴定"),
                        fallback(item.getEntrustOrgName(), "未知委托方"),
                        "COMPLETED".equals(item.getCaseStatus()) ? "已办结" : "流转中",
                        fallback(item.getCurrentNodeName(), "结束"),
                        "紧急度：" + (Integer.valueOf(1).equals(item.getUrgentFlag()) ? "紧急" : "普通"),
                        "更新时间：" + firstNonNull(item.getUpdatedTime(), item.getCreatedTime()),
                        firstNonNull(item.getUpdatedTime(), item.getCreatedTime()),
                        null,
                        List.of(fallback(item.getCaseType(), "司法鉴定"), "COMPLETED".equals(item.getCaseStatus()) ? "已办结" : "办理中"),
                        List.of(
                                "委托方：" + fallback(item.getEntrustOrgName(), "待补"),
                                "当前节点：" + fallback(item.getCurrentNodeName(), "已结束")
                        ),
                        "/case/" + item.getId()
                )).limit(20).toList(),
                List.of("导出 Excel", "PDF 导出", "导出统计汇总")
        );
    }

    private LedgerBoardDto unifiedTodoBoard(List<CaseInfo> cases, int rowLimit) {
        return personalTaskBoard(cases, rowLimit);
    }

    private LedgerBoardDto systemPermissionBoard(String status, int rowLimit) {
        if (sysRoleMapper == null || sysMenuMapper == null) {
            return simpleOfficeBoard("system-permission", "权限管理", "承接角色、菜单、数据范围和授权矩阵。");
        }
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0).orderByDesc(SysRole::getUpdatedTime).last("limit 200"));
        long menus = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getDeleted, 0));
        List<SysRole> filtered = roles.stream().filter(item -> roleMatchesStatus(item, status)).limit(rowLimit).toList();
        return new LedgerBoardDto("system-permission", "权限管理", "基于当前角色和菜单台账承接授权治理。", "live",
                List.of("all", "enabled", "disabled"),
                List.of(new LedgerMetricDto("角色", String.valueOf(roles.size()), false), new LedgerMetricDto("菜单", String.valueOf(menus), false), new LedgerMetricDto("启用角色", String.valueOf(roles.stream().filter(item -> "enabled".equals(item.getStatus())).count()), true), new LedgerMetricDto("数据范围", "5 类", false)),
                filtered.stream().map(item -> new LedgerRowDto(
                        "role-" + item.getId(),
                        fallback(item.getRoleName(), "未命名角色"),
                        fallback(item.getRoleCode(), "待补编码"),
                        fallback(item.getDataScope(), "待补范围"),
                        fallback(item.getRoleCode(), "角色"),
                        "enabled".equals(item.getStatus()) ? "启用" : "停用",
                        "数据范围：" + fallback(item.getDataScope(), "待补"),
                        "建议补角色矩阵、成员明细与授权范围",
                        "后续接权限变更审批与审计",
                        firstNonNull(item.getUpdatedTime(), item.getCreatedTime()),
                        null,
                        List.of("角色", "enabled".equals(item.getStatus()) ? "启用" : "停用"),
                        List.of(
                                "角色编码：" + fallback(item.getRoleCode(), "待补"),
                                "数据范围：" + fallback(item.getDataScope(), "待补"),
                                "创建人：" + fallback(String.valueOf(item.getCreatedBy()), "待补")
                        ),
                        "/admin/users"
                )).toList(),
                List.of("补角色矩阵", "补成员明细", "接入权限变更审批"));
    }

    private LedgerBoardDto systemLogBoard(String status, int rowLimit) {
        if (auditEventMapper == null) {
            return simpleOfficeBoard("system-log", "管理日志", "承接登录、下载、权限变更和业务审计查询。");
        }
        List<AuditEvent> events = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getOperatedTime).last("limit 200"));
        List<AuditEvent> filtered = events.stream().filter(item -> logMatchesStatus(item, status)).limit(rowLimit).toList();
        return new LedgerBoardDto("system-log", "管理日志", "基于审计事件承接系统与业务操作日志。", "live",
                List.of("all", "success", "failed"),
                List.of(new LedgerMetricDto("最近日志", String.valueOf(events.size()), false), new LedgerMetricDto("成功", String.valueOf(events.stream().filter(item -> "success".equalsIgnoreCase(item.getResultStatus())).count()), false), new LedgerMetricDto("失败", String.valueOf(events.stream().filter(item -> !"success".equalsIgnoreCase(item.getResultStatus())).count()), true), new LedgerMetricDto("审计覆盖", "已开启", false)),
                filtered.stream().map(item -> new LedgerRowDto(
                        "audit-" + item.getId(),
                        fallback(item.getActionName(), "未命名操作"),
                        fallback(item.getOperatorName(), "系统"),
                        fallback(item.getBizType(), "业务"),
                        fallback(item.getOperatorName(), "系统"),
                        "success".equalsIgnoreCase(item.getResultStatus()) ? "成功" : "失败",
                        fallback(item.getActionCode(), "待补编码"),
                        hasText(item.getDetailJson()) ? "已记录详情" : "建议补更多审计细节",
                        item.getCaseId() != null ? "可顺着跳回案件详情排查" : "后续接高级筛选与导出",
                        item.getOperatedTime(),
                        null,
                        List.of(fallback(item.getBizType(), "审计"), "success".equalsIgnoreCase(item.getResultStatus()) ? "成功" : "失败"),
                        List.of(
                                "业务ID：" + fallback(String.valueOf(item.getBizId()), "待补"),
                                "案件ID：" + fallback(String.valueOf(item.getCaseId()), "待补"),
                                "IP：" + fallback(item.getIpAddress(), "待补"),
                                "操作时间：" + formatRelative(item.getOperatedTime())
                        ),
                        casePath(item.getCaseId())
                )).toList(),
                List.of("补筛选条件", "补导出能力", "接入审计详情页"));
    }

    private boolean archiveMatchesStatus(CaseArchiveRecord item, String status) {
        return switch (status) {
            case "archived" -> "archived".equalsIgnoreCase(item.getArchiveStatus());
            case "pending" -> !"archived".equalsIgnoreCase(item.getArchiveStatus());
            default -> true;
        };
    }

    private boolean roleMatchesStatus(SysRole item, String status) {
        return switch (status) {
            case "enabled" -> "enabled".equalsIgnoreCase(item.getStatus());
            case "disabled" -> !"enabled".equalsIgnoreCase(item.getStatus());
            default -> true;
        };
    }

    private boolean logMatchesStatus(AuditEvent item, String status) {
        return switch (status) {
            case "success" -> "success".equalsIgnoreCase(item.getResultStatus());
            case "failed" -> !"success".equalsIgnoreCase(item.getResultStatus());
            default -> true;
        };
    }

    private boolean riskMatchesStatus(CaseInfo item, String status) {
        return switch (status) {
            case "overdue" -> isOverdue(item);
            case "urgent" -> Objects.equals(item.getUrgentFlag(), 1);
            default -> true;
        };
    }

    private boolean moduleStatusMatches(String moduleCode, LedgerRowDto row, String status) {
        if (!hasText(status) || "all".equals(status)) {
            return true;
        }
        String label = fallback(row.statusLabel(), "");
        return switch (moduleCode) {
            case "warehouse" -> switch (status) {
                case "borrowed" -> label.contains("归还");
                case "inbound" -> label.contains("入库");
                case "stock" -> label.contains("在库");
                default -> true;
            };
            case "notice" -> switch (status) {
                case "published" -> label.contains("已发布");
                case "draft" -> label.contains("草稿");
                case "pending" -> label.contains("待审核");
                default -> true;
            };
            case "meeting" -> switch (status) {
                case "scheduled" -> label.contains("待召开");
                case "minutes" -> label.contains("纪要");
                case "done" -> label.contains("已完成");
                default -> true;
            };
            case "asset" -> switch (status) {
                case "in-use" -> label.contains("在用");
                case "idle" -> label.contains("闲置");
                case "maintenance" -> label.contains("维修");
                default -> true;
            };
            case "attendance" -> switch (status) {
                case "normal" -> label.contains("正常");
                case "exception" -> label.contains("异常");
                case "leave" -> label.contains("请假");
                default -> true;
            };
            case "official-doc" -> switch (status) {
                case "draft" -> label.contains("草稿");
                case "reviewing" -> label.contains("流转");
                case "published" -> label.contains("已发布");
                default -> true;
            };
            case "community" -> switch (status) {
                case "hot" -> label.contains("热门");
                case "shared" -> label.contains("已分享");
                case "pending" -> label.contains("待审核");
                default -> true;
            };
            case "open-api" -> switch (status) {
                case "online" -> label.contains("在线");
                case "warning" -> label.contains("预警");
                case "draft" -> label.contains("草稿");
                default -> true;
            };
            case "sso" -> switch (status) {
                case "enabled" -> label.contains("启用");
                case "pending" -> label.contains("待配置");
                case "warning" -> label.contains("预警");
                default -> true;
            };
            default -> true;
        };
    }

    private String archiveStatusLabel(String status) {
        return "archived".equalsIgnoreCase(status) ? "已入库" : "待补充";
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

    private LedgerBoardDto moduleWorkBoard(String code, String name, String description,
                                           List<String> statusOptions,
                                           List<LedgerMetricDto> metrics,
                                           List<LedgerRowDto> rows,
                                           List<String> nextActions) {
        return new LedgerBoardDto(code, name, description, "structured", statusOptions, metrics, rows, nextActions);
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
                progressLabel, actionHint, updatedTime, deadlineTime, tags, facts, null);
    }

    private String casePath(CaseInfo item) {
        return item.getId() == null ? null : "/case/" + item.getId();
    }

    private String casePath(Long caseId) {
        return caseId == null ? null : "/case/" + caseId;
    }

    private LocalDateTime caseUpdatedTime(CaseInfo item) {
        return firstNonNull(item.getUpdatedTime(), item.getSubmittedTime(), item.getCreatedTime());
    }

    private boolean isOverdue(CaseInfo item) {
        return item.getDeadlineTime() != null && item.getDeadlineTime().isBefore(LocalDateTime.now())
                && !List.of("COMPLETED", "ARCHIVED", "TERMINATED").contains(item.getCaseStatus());
    }

    private String checkMysqlStatus() {
        if (dataSource == null) {
            return "未接入";
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "可用" : "异常";
        } catch (Exception ex) {
            return "异常";
        }
    }

    private String checkRedisStatus() {
        if (redisConnectionFactory == null) {
            return "未接入";
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong) ? "可用" : "异常";
        } catch (Exception ex) {
            return "异常";
        }
    }

    private String checkMinioStatus(String bucket) {
        if (minioClient == null || !hasText(bucket)) {
            return "未接入";
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            return exists ? "可用" : "异常";
        } catch (Exception ex) {
            return "异常";
        }
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

    private String contractDraftNumber(CaseInfo item) {
        String base = fallback(item.getCaseNo(), "HT-" + fallback(item.getId() == null ? null : item.getId().toString(), "TEMP"));
        return "HT-" + base.replace(" ", "-");
    }

    private String contractSigningWindow(CaseInfo item) {
        if (List.of("DRAFT", "TO_ACCEPT", "ACCEPT_REVIEWING", "REVIEWING").contains(item.getCaseStatus())) {
            return "本周内完成审批与签约确认";
        }
        if (List.of("PROCESSING", "DOC_ISSUING").contains(item.getCaseStatus())) {
            return "以履约跟踪为主，补签约归档字段";
        }
        return "转归档校验与归档补录";
    }

    private String projectMilestone(CaseInfo item) {
        if (List.of("DRAFT", "TO_ACCEPT", "ACCEPT_REVIEWING").contains(item.getCaseStatus())) {
            return "收案与受理";
        }
        if (List.of("REVIEWING", "PROCESSING").contains(item.getCaseStatus())) {
            return "实施与审核";
        }
        if ("DOC_ISSUING".equals(item.getCaseStatus())) {
            return "文书出具";
        }
        if (List.of("COMPLETED", "ARCHIVED").contains(item.getCaseStatus())) {
            return "结案归档";
        }
        return "异常收口";
    }

    private String projectCheckpoint(CaseInfo item) {
        if (isOverdue(item)) {
            return "补延期说明并重新确认节点责任";
        }
        if (Objects.equals(item.getUrgentFlag(), 1)) {
            return "优先锁定紧急节点与交付时间";
        }
        return "按当前节点推进并补里程碑说明";
    }

    private String customerTier(CustomerAggregate item) {
        if (item.caseCount() >= 3 || item.urgentCaseCount() >= 2) {
            return "A 级重点";
        }
        if (item.activeCaseCount() >= 1) {
            return "B 级维护";
        }
        return "C 级沉淀";
    }

    private LocalDateTime nextCustomerFollowUp(CustomerAggregate item) {
        if (item.urgentCaseCount() > 0) {
            return LocalDateTime.now().plusHours(24);
        }
        if (item.activeCaseCount() > 0) {
            return LocalDateTime.now().plusDays(3);
        }
        return LocalDateTime.now().plusDays(7);
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
        private String latestCaseTitle;
        private Long latestCaseId;
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
                latestCaseTitle = chooseText(item.getCaseTitle(), item.getCaseNo(), "待补");
                latestCaseId = item.getId();
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

        private String latestCaseTitle() {
            return latestCaseTitle;
        }

        private Long latestCaseId() {
            return latestCaseId;
        }

        private List<String> tags() {
            return tagBuffer.stream().filter(Objects::nonNull).distinct().limit(3).collect(Collectors.toList());
        }

        private String chooseText(String primary, String secondary, String fallback) {
            if (primary != null && !primary.isBlank()) {
                return primary.trim();
            }
            if (secondary != null && !secondary.isBlank()) {
                return secondary.trim();
            }
            return fallback;
        }
    }
}
