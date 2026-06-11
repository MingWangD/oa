package com.example.judicialappraisal.platform.service;

import com.example.judicialappraisal.platform.dto.JudicialCatalogDto;
import com.example.judicialappraisal.platform.dto.JudicialFormDefinitionDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowDefinitionDto;
import com.example.judicialappraisal.platform.dto.OaMenuItemDto;
import com.example.judicialappraisal.platform.dto.OaModuleDto;
import com.example.judicialappraisal.platform.dto.ReconstructionPhaseDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformCatalogService {

    private static final List<String> JUDICIAL_ROLES = List.of(
            "部门负责人", "项目负责人", "项目辅助人", "档案管理员", "中心档案管理员",
            "技术负责人", "审阅所长", "综合业务部", "财务", "收案员"
    );

    public List<OaMenuItemDto> menus() {
        return List.of(
                group("quick", "快捷菜单", "/placeholder/quick", 10,
                        leaf("quick-mail", "电子邮件", "/placeholder/quick/mail", "quick", "planned", 10),
                        leaf("quick-calendar", "日程", "/placeholder/quick/calendar", "quick", "planned", 20),
                        leaf("quick-knowledge", "知识库", "/knowledge", "knowledge", "implemented", 30)),
                group("personal", "个人事务", "/placeholder/personal", 20,
                        leaf("personal-message", "消息", "/placeholder/personal/message", "personal", "planned", 10),
                        leaf("personal-task", "任务", "/placeholder/personal/task", "personal", "planned", 20),
                        leaf("personal-log", "工作日志", "/placeholder/personal/log", "personal", "planned", 30),
                        leaf("personal-address", "通讯簿", "/placeholder/personal/address", "personal", "planned", 40)),
                group("workflow", "流程中心", "/my-work", 30,
                        leaf("workflow-new", "新建工作", "/case/new", "workflow", "partial", 10),
                        leaf("workflow-mine", "我的工作", "/my-work", "workflow", "partial", 20),
                        leaf("workflow-query", "工作查询", "/work-query", "workflow", "partial", 30),
                        leaf("workflow-monitor", "工作监控", "/placeholder/workflow/monitor", "workflow", "planned", 40),
                        leaf("workflow-timeout", "超时统计分析", "/placeholder/workflow/timeout", "workflow", "planned", 50),
                        leaf("workflow-delegate", "工作委托", "/placeholder/workflow/delegate", "workflow", "planned", 60),
                        leaf("workflow-destroy", "工作销毁", "/placeholder/workflow/destroy", "workflow", "planned", 70),
                        leaf("workflow-log", "流程日志查询", "/placeholder/workflow/log", "workflow", "planned", 80),
                        leaf("workflow-archive", "数据归档", "/placeholder/workflow/archive", "workflow", "planned", 90),
                        leaf("workflow-report", "数据报表", "/placeholder/workflow/report", "workflow", "planned", 100),
                        leaf("workflow-form-design", "设计表单", "/workflow/forms", "workflow", "partial", 110),
                        leaf("workflow-process-design", "设计流程", "/workflow/processes", "workflow", "partial", 120),
                        leaf("workflow-report-design", "报表设置", "/placeholder/workflow/report-setting", "workflow", "planned", 130),
                        leaf("workflow-datasource", "数据源管理", "/placeholder/workflow/datasource", "workflow", "planned", 140)),
                group("application", "应用中心", "/placeholder/application", 40,
                        leaf("application-mine", "我的应用", "/placeholder/application/mine", "application", "planned", 10),
                        leaf("application-design", "设计应用", "/placeholder/application/design", "application", "planned", 20)),
                group("business", "业务管理", "/placeholder/business", 50,
                        leaf("crm", "CRM", "/placeholder/crm", "crm", "planned", 10),
                        leaf("performance", "绩效", "/placeholder/performance", "performance", "planned", 20),
                        leaf("contract", "合同", "/placeholder/contract", "contract", "planned", 30),
                        leaf("project", "项目", "/placeholder/project", "project", "planned", 40),
                        leaf("warehouse", "仓库", "/placeholder/warehouse", "warehouse", "planned", 50),
                        leaf("risk", "安全风险", "/placeholder/risk", "risk", "planned", 60)),
                group("admin-office", "行政办公", "/placeholder/admin-office", 60,
                        leaf("notice", "公告新闻", "/placeholder/admin-office/notice", "admin-office", "planned", 10),
                        leaf("meeting", "会议", "/placeholder/admin-office/meeting", "admin-office", "planned", 20),
                        leaf("asset", "资产", "/placeholder/admin-office/asset", "admin-office", "planned", 30)),
                group("knowledge", "知识管理", "/knowledge", 70,
                        leaf("knowledge-base", "知识库", "/knowledge", "knowledge", "partial", 10),
                        leaf("knowledge-archive", "案件自动归档", "/placeholder/knowledge/archive", "knowledge", "planned", 20),
                        leaf("knowledge-disk", "网络硬盘", "/placeholder/knowledge/disk", "knowledge", "planned", 30)),
                group("governance", "督查/门户/报表", "/placeholder/governance", 80,
                        leaf("supervision", "督查督办", "/placeholder/supervision", "supervision", "planned", 10),
                        leaf("portal", "智能门户", "/placeholder/portal", "portal", "planned", 20),
                        leaf("report-center", "报表中心", "/placeholder/report-center", "report", "planned", 30)),
                group("people-docs", "人资/考勤/公文/档案", "/placeholder/people-docs", 90,
                        leaf("hr", "人力资源", "/placeholder/hr", "hr", "planned", 10),
                        leaf("attendance", "考勤", "/placeholder/attendance", "attendance", "planned", 20),
                        leaf("official-doc", "公文", "/placeholder/official-doc", "official-doc", "planned", 30),
                        leaf("archive", "档案", "/placeholder/archive", "archive", "planned", 40),
                        leaf("community", "交流园地", "/placeholder/community", "community", "planned", 50)),
                group("integration", "开放与集成平台", "/placeholder/integration", 100,
                        leaf("open-api", "外部系统集成", "/placeholder/integration/open-api", "integration", "planned", 10),
                        leaf("sso", "SSO", "/placeholder/integration/sso", "integration", "planned", 20),
                        leaf("unified-todo", "统一待办", "/placeholder/integration/todo", "integration", "planned", 30)),
                group("system", "系统管理", "/admin/users", 110,
                        leaf("system-users", "用户管理", "/admin/users", "system", "partial", 10),
                        leaf("system-permission", "权限管理", "/placeholder/system/permission", "system", "planned", 20),
                        leaf("system-log", "管理日志", "/placeholder/system/log", "system", "planned", 30),
                        leaf("system-datasource", "系统数据源", "/placeholder/system/datasource", "system", "planned", 40))
        );
    }

    public List<OaModuleDto> modules() {
        return List.of(
                module("platform", "公共平台底座", "菜单、权限、审计、附件、消息、数据权限", "P0", "in_progress",
                        "RBAC", "菜单与按钮权限", "组织范围数据权限", "统一审计", "附件服务"),
                module("workflow", "流程中心", "动态表单、动态流程、办理、监控、报表、归档", "P0", "in_progress",
                        "表单设计器", "流程设计器", "流程版本", "任务办理", "流程日志", "数据报表"),
                module("knowledge", "知识管理", "知识库、案件自动归档、文件预览下载、版本与权限", "P0", "in_progress",
                        "目录权限", "文件版本", "案件归档", "下载留痕", "全文检索"),
                module("judicial", "司法鉴定完整业务", "20 个流程、19 个表单、角色、文件、归档和版本", "P0", "in_progress",
                        "流程关联", "子流程", "并行任务", "退回路径", "意见书版本"),
                module("business-suite", "完整 OA 业务套件", "CRM、合同、项目、仓库、行政、人资、公文、档案等", "P1", "planned",
                        "业务台账", "审批状态", "统计导出", "数据权限"),
                module("integration", "开放与集成平台", "外部系统、SSO、统一待办、移动开放平台", "P2", "planned",
                        "接口认证", "回调验签", "幂等重试", "监控告警")
        );
    }

    public JudicialCatalogDto judicialCatalog() {
        List<JudicialWorkflowDefinitionDto> workflows = workflows();
        List<JudicialFormDefinitionDto> forms = forms();
        return new JudicialCatalogDto(workflows.size(), forms.size(), JUDICIAL_ROLES, workflows, forms);
    }

    public List<ReconstructionPhaseDto> reconstructionPlan() {
        return List.of(
                phase("第一阶段", "公共平台与可运行骨架", "completed",
                        "修复现有登录/组织/用户管理构建缺口",
                        "建立完整 OA 菜单与模块范围台账",
                        "建立司法鉴定 20 流程/19 表单元数据",
                        "前端补齐登录、首页、个人资料、用户管理和平台总览"),
                phase("第二阶段", "流程平台核心", "completed",
                        "动态表单草稿、预览、发布、恢复和版本管理",
                        "动态流程定义、节点、连线、条件表达式和版本发布",
                        "新建流程实例绑定最新已发布流程版本，历史实例保留原版本引用",
                        "自定义组织数据权限、动态出边流转、多出边并行任务和动态结束节点"),
                phase("第三阶段", "知识与文件平台", "in_progress",
                        "已新增 MinIO 文件上传、预览、下载和通用文件版本",
                        "已新增知识目录、文档、版本、目录权限、下载/预览审计",
                        "已将流程节点完成动作接入案件自动归档"),
                phase("第四阶段", "司法鉴定业务高保真", "in_progress",
                        "已提供 20 个流程/19 个表单的平台配置导入接口",
                        "已将输入/输出文件、版本产物、角色节点和退回路径写入设计器配置",
                        "下一步逐条跑通细化流程条件、并行、子流程和流程关联"),
                phase("第五阶段", "完整 OA 业务域", "planned",
                        "CRM、绩效、合同、项目、仓库、安全风险",
                        "行政、人资、考勤、公文、档案、交流园地",
                        "督查督办、门户、报表中心、开放集成"),
                phase("第六阶段", "验收、迁移和运维治理", "planned",
                        "历史数据迁移工具和校验报告",
                        "权限、安全、审计与性能验收",
                        "部署监控、备份恢复和运维文档")
        );
    }

    private List<JudicialWorkflowDefinitionDto> workflows() {
        return List.of(
                workflow("received-entrust", "收到委托书", "received-entrust", "direct", List.of("收案员", "部门负责人", "项目负责人", "项目辅助人"),
                        List.of("任何授权用户可新建", "部门负责人决定是否受理并指定项目负责人", "项目负责人判断初步勘验并指定辅助人"),
                        List.of("初步勘验", "发交费通知书及相关函件", "不予受理")),
                workflow("preliminary-survey", "初步勘验", "preliminary-survey", "subflow", List.of("项目负责人", "项目辅助人"),
                        List.of("上传现场工作方案与设备记录", "项目负责人审核是否具备鉴定条件"),
                        List.of("发交费通知书及相关函件", "终止鉴定")),
                workflow("payment-notice", "发交费通知书及相关函件", "payment-notice", "subflow", List.of("项目负责人", "项目辅助人", "档案管理员"),
                        List.of("函件编制、审核、用章", "项目负责人确认是否缴费"),
                        List.of("编制内部质量控制文件", "终止鉴定")),
                workflow("quality-control", "编制内部质量控制文件", "quality-control", "subflow", List.of("项目辅助人", "项目负责人", "部门负责人", "档案管理员"),
                        List.of("中心格式且金额大于 50 万或非中心格式且金额大于 25 万判定 F 类", "F 类需部门负责人审核"),
                        List.of("现场勘验", "材料接收与返还", "鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定")),
                workflow("field-survey", "现场勘验", "field-survey", "subflow", List.of("项目辅助人", "项目负责人", "技术负责人", "部门负责人"),
                        List.of("项目金额大于 15 万需项目负责人、技术负责人、部门负责人逐级审核", "勘验后判断补材、报告、退费和终止"),
                        List.of("材料接收与返还", "鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定")),
                workflow("material-receive-return", "材料接收与返还", "material-receive-return", "subflow", List.of("项目负责人", "项目辅助人", "档案管理员"),
                        List.of("区分委托方直接提供和补充材料", "登记介质类别、存放地址和是否归还"),
                        List.of("鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定", "归档")),
                workflow("draft-opinion-review", "鉴定意见书征求意见稿送审稿编制", "draft-opinion-review", "subflow", List.of("项目负责人", "项目辅助人", "技术负责人", "部门负责人"),
                        List.of("项目辅助人编制初稿", "项目负责人、技术负责人、部门负责人三级审核", "项目负责人上传定稿"),
                        List.of("出具征求意见稿")),
                workflow("final-opinion-review", "鉴定意见书送审稿编制", "final-opinion-review", "subflow", List.of("项目负责人", "项目辅助人", "技术负责人", "部门负责人"),
                        List.of("保留初稿、A、A-B、A-B-C、最终送审稿版本", "三级审核不得合并"),
                        List.of("出具鉴定意见书")),
                workflow("issue-opinion", "出具鉴定意见书", "issue-opinion", "subflow", List.of("项目负责人", "项目辅助人", "档案管理员"),
                        List.of("上传承诺书与复核意见", "盖章、开票、扫描件回传"),
                        List.of("归档")),
                workflow("issue-draft-opinion", "出具征求意见稿", "issue-draft-opinion", "subflow", List.of("项目辅助人", "项目负责人", "档案管理员"),
                        List.of("编制说明函、用章、寄出", "等待反馈并判断是否收到异议函"),
                        List.of("收到法院其他函件（含异议函）", "出具鉴定意见书", "归档")),
                workflow("court-letter", "收到法院其他函件（含异议函）", "court-letter", "direct-or-linked", List.of("收件人", "项目负责人", "项目辅助人", "档案管理员"),
                        List.of("可手动新建并关联原流程", "识别异议函或其他函件", "回复、用章、寄出后归档"),
                        List.of("归档", "出具鉴定意见书")),
                workflow("court-appearance", "收到出庭通知", "court-appearance", "direct-or-linked", List.of("收件人", "项目负责人", "档案管理员"),
                        List.of("关联原流程", "调档、出庭准备、出庭材料整理"),
                        List.of("归档")),
                workflow("reject-acceptance", "不予受理", "reject-acceptance", "subflow", List.of("项目辅助人", "项目负责人", "档案管理员"),
                        List.of("编制不予受理通知书", "审核、用章、扫描件上传"),
                        List.of("归档")),
                workflow("withdraw-case-letter", "收到撤案函", "withdraw-case-letter", "direct-or-linked", List.of("收件人", "项目负责人"),
                        List.of("登记撤案函", "项目负责人判断是否退费"),
                        List.of("退费", "终止鉴定")),
                workflow("refund", "退费", "refund", "subflow", List.of("项目负责人", "档案管理员", "财务"),
                        List.of("合同变更、收入确认、退费申请、打款", "打款成功后进入终止鉴定"),
                        List.of("终止鉴定")),
                workflow("terminate-appraisal", "终止鉴定", "terminate-appraisal", "subflow", List.of("项目负责人", "项目辅助人", "档案管理员"),
                        List.of("编制终止函或终止确认函", "审核、用章、扫描件上传"),
                        List.of("归档")),
                workflow("archive", "归档", "archive", "subflow", List.of("档案管理员", "中心档案管理员", "邮寄人员"),
                        List.of("上传项目档案、纸质扫描、电子归档地址", "邮寄后中心档案管理员审核并入档案室"),
                        List.of("流程结束")),
                workflow("seal-application", "用章流程/用章申请表", "seal-application", "subflow-or-direct", List.of("申请人", "档案管理员", "盖章经办人"),
                        List.of("填写申请文件和附件", "线下或电子盖章后扫描件回传"),
                        List.of("返回父流程")),
                workflow("expense-reimbursement", "财务报销", "expense-reimbursement", "direct", List.of("发起人", "财务"),
                        List.of("独立发起", "上传报销材料后财务处理并登记结果"),
                        List.of("流程结束")),
                workflow("case-suspension", "案件暂停", "case-suspension", "subflow", List.of("项目负责人", "授权审批人"),
                        List.of("记录暂停原因、开始时间、预计恢复时间", "恢复后回原节点或进入终止"),
                        List.of("恢复办理", "终止鉴定"))
        );
    }

    private List<JudicialFormDefinitionDto> forms() {
        return List.of(
                form("received-entrust", "收到委托书", "", List.of("委托书", "初始材料"), List.of("收案登记信息"), List.of()),
                form("reject-acceptance", "不予受理", "", List.of("委托审查结果"), List.of("不予受理通知书扫描件"), List.of()),
                form("payment-notice", "发交费通知书及相关函件", "", List.of("缴费函件草稿"), List.of("盖章函件", "缴费确认"), List.of()),
                form("material-receive-return", "材料接收与返还", "", List.of("补充材料", "委托方直接提交材料"), List.of("材料登记表", "返还登记表"), List.of()),
                form("archive", "归档", "", List.of("项目档案", "纸质扫描件"), List.of("电子归档地址", "中心入库记录"), List.of()),
                form("seal-application", "用章申请表", "用章流程", List.of("需盖章文件"), List.of("盖章扫描件"), List.of()),
                form("preliminary-survey", "初步勘验", "", List.of("现场工作方案"), List.of("设备出入库记录", "设备使用记录"), List.of()),
                form("terminate-appraisal", "终止鉴定", "", List.of("终止原因材料"), List.of("鉴定终止函", "鉴定终止确认函", "盖章扫描件"), List.of()),
                form("quality-control", "编制内部质量控制文件", "", List.of("质量控制文件草稿"), List.of("内部质量控制文件盖章件"), List.of()),
                form("field-survey", "现场勘验", "", List.of("现场工作方案"), List.of("勘验记录", "设备记录"), List.of()),
                form("draft-opinion-review", "鉴定意见书征求意见稿送审稿编制", "", List.of("鉴定意见书初稿"), List.of("征求意见稿送审稿定稿"), List.of("初稿", "项目负责人审核稿", "技术负责人审核稿", "部门负责人审核稿")),
                form("final-opinion-review", "鉴定意见书送审稿编制", "", List.of("鉴定意见书初稿"), List.of("鉴定意见书送审稿确定版本"), List.of("初稿", "A", "A-B", "A-B-C", "最终送审稿")),
                form("refund", "退费", "", List.of("退费申请材料", "合同变更材料"), List.of("打款结果", "终止鉴定触发记录"), List.of()),
                form("withdraw-case-letter", "撤案函/收到撤案函", "收到撤案函", List.of("撤案函"), List.of("退费或终止判断结果"), List.of()),
                form("expense-reimbursement", "财务报销", "", List.of("报销材料"), List.of("财务处理结果"), List.of()),
                form("issue-opinion", "出具鉴定意见书", "", List.of("鉴定意见书送审稿", "承诺书", "复核意见"), List.of("鉴定意见书盖章件", "开票记录"), List.of("最终出具版本")),
                form("court-appearance", "收到出庭通知", "", List.of("出庭通知"), List.of("调档记录", "出庭材料", "归档材料"), List.of()),
                form("issue-draft-opinion", "出具征求意见稿", "", List.of("征求意见稿送审稿"), List.of("鉴定说明函", "征求意见稿盖章件", "寄送记录"), List.of()),
                form("court-letter", "收到法院其他函件（含异议函）", "", List.of("法院函件", "异议函"), List.of("异议回复函", "其他函件回复", "寄送记录"), List.of())
        );
    }

    private OaMenuItemDto group(String code, String title, String path, int sortNo, OaMenuItemDto... children) {
        return new OaMenuItemDto(code, title, path, code, "cataloged", sortNo, List.of(children));
    }

    private OaMenuItemDto leaf(String code, String title, String path, String module, String status, int sortNo) {
        return new OaMenuItemDto(code, title, path, module, status, sortNo, List.of());
    }

    private OaModuleDto module(String code, String name, String scope, String priority, String status, String... capabilities) {
        return new OaModuleDto(code, name, scope, priority, status, List.of(capabilities));
    }

    private ReconstructionPhaseDto phase(String phase, String goal, String status, String... deliverables) {
        return new ReconstructionPhaseDto(phase, goal, status, List.of(deliverables));
    }

    private JudicialWorkflowDefinitionDto workflow(String code, String name, String formCode, String entryMode,
                                                   List<String> roles, List<String> keyRules, List<String> nextFlows) {
        return new JudicialWorkflowDefinitionDto(code, name, formCode, entryMode, roles, keyRules, nextFlows);
    }

    private JudicialFormDefinitionDto form(String code, String name, String alias, List<String> inputFiles,
                                           List<String> outputFiles, List<String> versionedArtifacts) {
        return new JudicialFormDefinitionDto(code, name, alias, inputFiles, outputFiles, versionedArtifacts);
    }
}
