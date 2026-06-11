package com.example.judicialappraisal.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.platform.dto.JudicialConfigImportResult;
import com.example.judicialappraisal.workflow.design.FormDesignService;
import com.example.judicialappraisal.workflow.design.WorkflowDesignService;
import com.example.judicialappraisal.workflow.design.dto.FormDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDesignRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JudicialConfigImportServiceTests {

    private final PlatformCatalogService platformCatalogService = new PlatformCatalogService();
    private final FormDesignService formDesignService = mock(FormDesignService.class);
    private final WorkflowDesignService workflowDesignService = mock(WorkflowDesignService.class);
    private final JudicialConfigImportService service = new JudicialConfigImportService(
            platformCatalogService,
            formDesignService,
            workflowDesignService,
            new ObjectMapper()
    );

    @Test
    void importCatalogPublishesNineteenFormsAndTwentyWorkflows() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        JudicialConfigImportResult result = service.importCatalog(false);

        assertThat(result.formsCreated()).isEqualTo(19);
        assertThat(result.workflowsCreated()).isEqualTo(20);
        verify(formDesignService, times(19)).saveDraft(any(FormDesignRequest.class));
        verify(formDesignService).publish(eq("received-entrust"));
        verify(workflowDesignService, times(20)).saveDraft(any(WorkflowDesignRequest.class));
        verify(workflowDesignService).publish(eq("archive"));
    }

    @Test
    void receivedEntrustImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest receivedEntrustForm = formCaptor.getAllValues().stream()
                .filter(request -> "received-entrust".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(receivedEntrustForm.fieldSchemaJson()).contains("clientName", "entrustAccepted", "preliminarySurveyRequired");
        assertThat(receivedEntrustForm.validationSchemaJson()).contains("duplicateAttachmentPolicy", "reject");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest receivedEntrustWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "received-entrust".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(receivedEntrustWorkflow.nodes()).extracting("nodeCode")
                .contains("CLERK_REGISTER", "DEPT_REVIEW", "PROJECT_DECISION", "PRELIMINARY_SURVEY", "PAYMENT_NOTICE", "REJECT_ACCEPTANCE");
        assertThat(receivedEntrustWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.entrustAccepted == true", "form.entrustAccepted == false", "form.preliminarySurveyRequired == true");
        assertThat(receivedEntrustWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "reject-acceptance"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "preliminary-survey"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "payment-notice"));
    }

    @Test
    void preliminarySurveyImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest preliminarySurveyForm = formCaptor.getAllValues().stream()
                .filter(request -> "preliminary-survey".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(preliminarySurveyForm.fieldSchemaJson())
                .contains("surveyPlanUploaded", "equipmentOutboundRecorded", "equipmentUsageRecorded", "appraisalConditionMet");
        assertThat(preliminarySurveyForm.validationSchemaJson())
                .contains("crossFieldRules", "nextRecommendation");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest preliminarySurveyWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "preliminary-survey".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(preliminarySurveyWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_PREPARE", "PROJECT_REVIEW", "PAYMENT_NOTICE", "TERMINATE_APPRAISAL");
        assertThat(preliminarySurveyWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.appraisalConditionMet == true", "form.appraisalConditionMet == false");
        assertThat(preliminarySurveyWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "payment-notice"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void paymentNoticeImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest paymentNoticeForm = formCaptor.getAllValues().stream()
                .filter(request -> "payment-notice".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(paymentNoticeForm.fieldSchemaJson())
                .contains("letterDraftCompleted", "sealRequired", "sealedDocumentUploaded", "paymentReceived");
        assertThat(paymentNoticeForm.validationSchemaJson())
                .contains("crossFieldRules", "nextRecommendation", "sealedDocumentUploaded == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest paymentNoticeWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "payment-notice".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(paymentNoticeWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_DRAFT", "PROJECT_REVIEW", "SEAL_APPLICATION", "ARCHIVE_UPLOAD", "PAYMENT_CONFIRM", "QUALITY_CONTROL", "TERMINATE_APPRAISAL");
        assertThat(paymentNoticeWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.sealRequired == true", "form.sealRequired == false", "form.paymentReceived == true", "form.paymentReceived == false");
        assertThat(paymentNoticeWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "quality-control"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void qualityControlImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest qualityControlForm = formCaptor.getAllValues().stream()
                .filter(request -> "quality-control".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(qualityControlForm.fieldSchemaJson())
                .contains("qualityFileDraftCompleted", "formatType", "contractAmount", "fClassProject", "projectReviewRoute", "nextRecommendation");
        assertThat(qualityControlForm.validationSchemaJson())
                .contains("contractAmount > 500000", "contractAmount > 250000", "projectReviewRoute == '部门负责人审核'");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest qualityControlWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "quality-control".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(qualityControlWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_DRAFT", "PROJECT_REVIEW", "DEPARTMENT_REVIEW", "SEAL_APPLICATION", "SEALED_FILE_UPLOAD", "NEXT_FLOW_DECISION",
                        "FIELD_SURVEY", "MATERIAL_RECEIVE_RETURN", "DRAFT_OPINION_REVIEW", "FINAL_OPINION_REVIEW", "REFUND", "TERMINATE_APPRAISAL");
        assertThat(qualityControlWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewRoute == '部门负责人审核'", "form.projectReviewRoute == '进入用章'", "form.departmentReviewPassed == true",
                        "form.nextRecommendation == '现场勘验'", "form.nextRecommendation == '鉴定意见书送审稿编制'");
        assertThat(qualityControlWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "field-survey"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "material-receive-return"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "draft-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "refund"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void fieldSurveyImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest fieldSurveyForm = formCaptor.getAllValues().stream()
                .filter(request -> "field-survey".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(fieldSurveyForm.fieldSchemaJson())
                .contains("surveyPlanUploaded", "fieldRecordUploaded", "equipmentUsageRecorded", "projectAmount", "majorAmountProject", "projectReviewRoute");
        assertThat(fieldSurveyForm.validationSchemaJson())
                .contains("projectAmount > 150000", "projectReviewRoute == '技术负责人审核'", "equipmentUsageRecorded == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest fieldSurveyWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "field-survey".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(fieldSurveyWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_SURVEY", "PROJECT_REVIEW", "TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "NEXT_FLOW_DECISION",
                        "MATERIAL_RECEIVE_RETURN", "DRAFT_OPINION_REVIEW", "FINAL_OPINION_REVIEW", "REFUND", "TERMINATE_APPRAISAL");
        assertThat(fieldSurveyWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewRoute == '技术负责人审核'", "form.projectReviewRoute == '确认后续流程'",
                        "form.technicalReviewPassed == true", "form.departmentReviewPassed == true",
                        "form.nextRecommendation == '材料接收与返还'", "form.nextRecommendation == '退费'");
        assertThat(fieldSurveyWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "material-receive-return"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "draft-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "refund"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void rejectAcceptanceImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest rejectAcceptanceForm = formCaptor.getAllValues().stream()
                .filter(request -> "reject-acceptance".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(rejectAcceptanceForm.fieldSchemaJson())
                .contains("rejectionReason", "noticeDraftCompleted", "projectReviewPassed", "sealedNoticeUploaded", "archiveConfirmed");
        assertThat(rejectAcceptanceForm.validationSchemaJson())
                .contains("crossFieldRules", "projectReviewPassed == true", "sealedNoticeUploaded == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest rejectAcceptanceWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "reject-acceptance".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(rejectAcceptanceWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_DRAFT", "PROJECT_REVIEW", "SEAL_APPLICATION", "SEALED_NOTICE_UPLOAD", "DELIVERY_ARCHIVE", "ARCHIVE");
        assertThat(rejectAcceptanceWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewPassed == true", "form.projectReviewPassed == false", "form.archiveConfirmed == true");
        assertThat(rejectAcceptanceWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }

    @Test
    void materialReceiveReturnImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest materialReceiveReturnForm = formCaptor.getAllValues().stream()
                .filter(request -> "material-receive-return".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(materialReceiveReturnForm.fieldSchemaJson())
                .contains("materialSource", "requireSupplementaryMaterial", "materialDetails", "requireReturn", "nextRecommendation");
        assertThat(materialReceiveReturnForm.validationSchemaJson())
                .contains("requireReturn == true", "returnReceiver != null");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(20)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest materialReceiveReturnWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "material-receive-return".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(materialReceiveReturnWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_CONFIRM", "ASSISTANT_REGISTER", "ARCHIVIST_HANDLE", "PROJECT_DECISION",
                        "DRAFT_OPINION_REVIEW", "FINAL_OPINION_REVIEW", "REFUND", "TERMINATE_APPRAISAL", "ARCHIVE");
        assertThat(materialReceiveReturnWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", "form.nextRecommendation == '终止鉴定'", "form.nextRecommendation == '归档'");
        assertThat(materialReceiveReturnWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "draft-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "refund"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }
}
