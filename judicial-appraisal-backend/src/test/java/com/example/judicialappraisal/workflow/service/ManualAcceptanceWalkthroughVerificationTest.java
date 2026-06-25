package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.judicialappraisal.auth.dto.LoginRequest;
import com.example.judicialappraisal.auth.dto.LoginResponse;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class ManualAcceptanceWalkthroughVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JudicialConfigImportService judicialConfigImportService;

    @BeforeEach
    void setUp() {
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    @Transactional
    void fullWalkthrough_NewWork_Draft_Delete_Submit_Chain() throws Exception {
        // --- 1. case_acceptor 登录 ---
        String acceptorToken = getToken("case_acceptor1", "123456");
        Long acceptorId = getUserId("case_acceptor1");
        Long acceptorDeptId = getDeptId("case_acceptor1");

        // --- 2. 验证“新建工作”权限过滤 ---
        MvcResult availableResult = mockMvc.perform(get("/api/platform/judicial-catalog/available")
                .header("Authorization", "Bearer " + acceptorToken))
                .andExpect(status().isOk())
                .andReturn();
        String availableBody = availableResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(availableBody).contains("收到委托书");

        // --- 3. 新建并删除草稿 ---
        CaseCreateRequest createReq = new CaseCreateRequest("真实流程链条验证", "司法鉴定", "测试法院", acceptorDeptId);
        MvcResult createResult = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + acceptorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();
        Long caseId = objectMapper.readValue(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8), 
                new TypeReference<ApiResponse<CaseInfo>>() {}).data().getId();

        mockMvc.perform(delete("/api/cases/" + caseId)
                .header("Authorization", "Bearer " + acceptorToken))
                .andExpect(status().isOk());
        
        Integer deleted = jdbcTemplate.queryForObject("SELECT deleted FROM case_info WHERE id = " + caseId, Integer.class);
        assertThat(deleted).isEqualTo(1);

        // --- 4. 重新新建并提交 ---
        createResult = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + acceptorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();
        caseId = objectMapper.readValue(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8), 
                new TypeReference<ApiResponse<CaseInfo>>() {}).data().getId();

        CaseSubmitRequest submitReq = new CaseSubmitRequest(acceptorId, "收案员", "发起验证");
        mockMvc.perform(post("/api/cases/" + caseId + "/submit")
                .header("Authorization", "Bearer " + acceptorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk());

        // --- 5. case_acceptor 完成 INIT_FILL 节点 ---
        Long taskId = getTaskId(caseId, "INIT_FILL");
        Map<String, Object> formData = new java.util.HashMap<>();
        formData.put("entrustOrgName", "测试法院");
        formData.put("appraisalCategory", "法医临床");
        formData.put("urgencyLevel", "普通");
        formData.put("caseNo", "REAL-CHAIN-001");
        formData.put("caseChannel", "线下");
        
        // --- 补充必填字段，满足权限系统要求 ---
        formData.put("receivedDate", "2026-06-20");
        formData.put("filingDate", "2026-06-20");
        formData.put("clientName", "测试委托人");
        formData.put("undertakingLegalPerson", "测试法人");
        formData.put("institutionSelectionMethod", "抽签");
        formData.put("institutionSelectionTime", "2026-06-20");
        formData.put("applicantName", "张三申请人");
        formData.put("respondentName", "李四被申请人");
        formData.put("appraisalMatter", "法医临床鉴定");

        WorkflowActionRequest initFillReq = new WorkflowActionRequest(taskId, ActionCode.APPROVE, "填写完成", null, null, null, formData, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/actions")
                .header("Authorization", "Bearer " + acceptorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initFillReq)))
                .andExpect(status().isOk());

        // --- 6. case_acceptor 完成 CLERK_REGISTER 节点 ---
        taskId = getTaskId(caseId, "CLERK_REGISTER");
        WorkflowActionRequest clerkRegReq = new WorkflowActionRequest(taskId, ActionCode.APPROVE, "登记完成", null, null, null, Map.of(), null);
        mockMvc.perform(post("/api/cases/" + caseId + "/actions")
                .header("Authorization", "Bearer " + acceptorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clerkRegReq)))
                .andExpect(status().isOk());

        // --- 7. 换 dept_leader 登录验证待办 ---
        // 关键：确保 dept_leader 也在该部门，以符合 DEPT 数据权限
        jdbcTemplate.update("UPDATE sys_user SET dept_id = ? WHERE username = 'dept_leader1'", acceptorDeptId);

        String leaderToken = getToken("dept_leader1", "123456");
        Long leaderId = getUserId("dept_leader1");

        MvcResult todoResult = mockMvc.perform(get("/api/workbench/todo?assigneeId=" + leaderId)
                .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andReturn();
        String todoBody = todoResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(todoBody).contains("真实流程链条验证");

        // --- 8. dept_leader 办理 DEPT_REVIEW ---
        taskId = getTaskId(caseId, "DEPT_REVIEW");
        WorkflowActionRequest deptReviewReq = new WorkflowActionRequest(taskId, ActionCode.APPROVE, "准予受理", null, null, null, 
                Map.of("entrustAccepted", true, "projectLeaderId", 7L, "projectAssistantId", 6L), null);
        mockMvc.perform(post("/api/cases/" + caseId + "/actions")
                .header("Authorization", "Bearer " + leaderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deptReviewReq)))
                .andExpect(status().isOk());

        // --- 9. 验证工作查询详情 ---
        MvcResult detailResult = mockMvc.perform(get("/api/cases/" + caseId)
                .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andReturn();
        String detailBody = detailResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(detailBody)
                .contains("REAL-CHAIN-001")
                .contains("真实流程链条验证")
                .contains("项目负责人决策");
        
        // 验证已办能看到详情
        MvcResult doneResult = mockMvc.perform(get("/api/workbench/done?assigneeId=" + leaderId)
                .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andReturn();
        String doneBody = doneResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(doneBody).contains("真实流程链条验证");
    }

    private String getToken(String username, String password) throws Exception {
        LoginRequest loginReq = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<LoginResponse> resp = objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), 
                new TypeReference<ApiResponse<LoginResponse>>() {});
        return resp.data().token();
    }

    private Long getUserId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private Long getDeptId(String username) {
        return jdbcTemplate.queryForObject("SELECT dept_id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private Long getTaskId(Long caseId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM case_task WHERE case_id = ? AND node_code = ? AND status IN ('pending', 'claimed', 'processing') ORDER BY id DESC LIMIT 1", 
                Long.class, caseId, nodeCode);
    }
}
