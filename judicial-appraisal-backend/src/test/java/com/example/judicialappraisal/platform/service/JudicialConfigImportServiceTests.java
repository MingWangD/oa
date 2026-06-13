package com.example.judicialappraisal.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    void importCatalogPublishesNineteenFormsAndNineteenWorkflows() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        JudicialConfigImportResult result = service.importCatalog(false);

        assertThat(result.formsCreated()).isEqualTo(19);
        assertThat(result.workflowsCreated()).isEqualTo(19);
        verify(formDesignService, times(19)).saveDraft(any(FormDesignRequest.class));
        verify(formDesignService).publish(eq("received-entrust"));
        verify(workflowDesignService, times(19)).saveDraft(any(WorkflowDesignRequest.class));
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
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
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
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
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
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
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
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
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
                .contains("surveyPlanUploaded", "fieldRecordUploaded", "equipmentOutboundRecorded", "equipmentUsageRecorded",
                        "projectAmount", "majorAmountProject", "projectReviewRoute", "projectMaterialReviewPassed");
        assertThat(fieldSurveyForm.validationSchemaJson())
                .contains("projectAmount > 150000", "projectReviewRoute == '技术负责人审核'",
                        "equipmentOutboundRecorded == true", "equipmentUsageRecorded == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest fieldSurveyWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "field-survey".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(fieldSurveyWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_SURVEY", "PROJECT_REVIEW", "TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "PROJECT_TO_EQUIPMENT",
                        "ASSISTANT_EQUIPMENT", "PROJECT_MATERIAL_REVIEW", "NEXT_FLOW_DECISION", "MATERIAL_RECEIVE_RETURN",
                        "DRAFT_OPINION_REVIEW", "FINAL_OPINION_REVIEW", "REFUND", "TERMINATE_APPRAISAL");
        assertThat(fieldSurveyWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewRoute == '技术负责人审核'", "form.projectReviewRoute == '确认后续流程'",
                        "form.technicalReviewPassed == true", "form.departmentReviewPassed == true",
                        "form.projectMaterialReviewPassed == true", "form.projectMaterialReviewPassed == false",
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
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
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
                .contains("materialReceiveType", "materialUploaderId", "supplementaryNoticeUploaded",
                        "materialsUploaded", "projectMaterialConfirmed", "materialMediaType",
                        "returnRegistrationCompleted", "requireReturn", "nextRecommendation");
        assertThat(materialReceiveReturnForm.validationSchemaJson())
                .contains("requireSupplementaryMaterial == true", "supplementaryNoticeUploaded == true",
                        "materialReceiveType == '委托方直接提供'", "materialUploaderId != null",
                        "requireReturn == true", "returnRegistrationCompleted == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest materialReceiveReturnWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "material-receive-return".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(materialReceiveReturnWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_CONFIRM", "MATERIAL_UPLOAD", "PROJECT_MATERIAL_CONFIRM", "PAYMENT_NOTICE",
                        "ASSISTANT_REGISTER", "ASSISTANT_RETURN", "ARCHIVIST_HANDLE", "PARALLEL_GATEWAY_SPLIT", "PROJECT_DECISION",
                        "DRAFT_OPINION_REVIEW", "FINAL_OPINION_REVIEW", "REFUND", "TERMINATE_APPRAISAL", "ARCHIVE");
        assertThat(materialReceiveReturnWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.materialReceiveType == '委托方直接提供'", "form.requireSupplementaryMaterial == true",
                        "form.projectMaterialConfirmed == true", "form.projectMaterialConfirmed == false",
                        "form.requireReturn == true", "form.requireReturn == false",
                        "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", "form.nextRecommendation == '终止鉴定'");
        assertThat(materialReceiveReturnWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "payment-notice"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "draft-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "refund"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void draftOpinionReviewImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest draftOpinionReviewForm = formCaptor.getAllValues().stream()
                .filter(request -> "draft-opinion-review".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(draftOpinionReviewForm.fieldSchemaJson())
                .contains("draftOpinionUploaded", "projectReviewPassed", "technicalReviewPassed", "departmentReviewPassed", "finalDraftUploaded", "nextRecommendation");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest draftOpinionReviewWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "draft-opinion-review".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(draftOpinionReviewWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_ASSIGN", "ASSISTANT_DRAFT", "PROJECT_REVIEW", "TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "PROJECT_FINAL_UPLOAD", "ISSUE_DRAFT_OPINION");
        assertThat(draftOpinionReviewWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewPassed == true", "form.technicalReviewPassed == true", "form.departmentReviewPassed == true", "form.nextRecommendation == '出具征求意见稿'");
        assertThat(draftOpinionReviewWorkflow.transitions()).extracting("fromNodeCode", "toNodeCode")
                .contains(tuple("START", "PROJECT_ASSIGN"), tuple("PROJECT_ASSIGN", "ASSISTANT_DRAFT"));
        assertThat(draftOpinionReviewWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "issue-draft-opinion"));
    }

    @Test
    void finalOpinionReviewImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest finalOpinionReviewForm = formCaptor.getAllValues().stream()
                .filter(request -> "final-opinion-review".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(finalOpinionReviewForm.fieldSchemaJson())
                .contains("opinionDraftUploaded", "projectReviewPassed", "versionAUploaded", "technicalReviewPassed", "versionABUploaded", "departmentReviewPassed", "versionABCUploaded", "finalDraftUploaded", "nextRecommendation");
        assertThat(finalOpinionReviewForm.validationSchemaJson())
                .contains("versionAUploaded == true", "versionABUploaded == true", "versionABCUploaded == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest finalOpinionReviewWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "final-opinion-review".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(finalOpinionReviewWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_ASSIGN", "ASSISTANT_DRAFT", "PROJECT_REVIEW", "TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "PROJECT_FINAL_UPLOAD", "ISSUE_OPINION");
        assertThat(finalOpinionReviewWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewPassed == true", "form.technicalReviewPassed == true", "form.departmentReviewPassed == true", "form.nextRecommendation == '出具鉴定意见书'");
        assertThat(finalOpinionReviewWorkflow.transitions()).extracting("fromNodeCode", "toNodeCode")
                .contains(tuple("START", "PROJECT_ASSIGN"), tuple("PROJECT_ASSIGN", "ASSISTANT_DRAFT"));
        assertThat(finalOpinionReviewWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "issue-opinion"));
    }

    @Test
    void issueOpinionImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest issueOpinionForm = formCaptor.getAllValues().stream()
                .filter(request -> "issue-opinion".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(issueOpinionForm.fieldSchemaJson())
                .contains("commitmentDrafted", "reviewOpinionDrafted", "sealRequired", "sealedOpinionUploaded", "invoiceRequired", "invoiceIssued", "archiveConfirmed");
        assertThat(issueOpinionForm.validationSchemaJson())
                .contains("sealRequired == true", "invoiceRequired == true", "archiveConfirmed == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest issueOpinionWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "issue-opinion".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(issueOpinionWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_SUPPLEMENT", "SEAL_APPLICATION", "SEALED_UPLOAD", "FINANCE_INVOICE", "DELIVERY_ARCHIVE", "ARCHIVE_SUBFLOW");
        assertThat(issueOpinionWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.sealRequired == true", "form.sealRequired == false", "form.invoiceRequired == true", "form.invoiceRequired == false", "form.archiveConfirmed == true");
        assertThat(issueOpinionWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }

    @Test
    void issueDraftOpinionImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest issueDraftOpinionForm = formCaptor.getAllValues().stream()
                .filter(request -> "issue-draft-opinion".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(issueDraftOpinionForm.fieldSchemaJson())
                .contains("explainLetterDrafted", "sealRequired", "sealedDraftOpinionUploaded", "feedbackReceived", "feedbackHasObjection", "feedbackDecision", "objectionReason");
        assertThat(issueDraftOpinionForm.validationSchemaJson())
                .contains("sealRequired == true", "feedbackDecision == '收到异议'", "objectionReason != null");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest issueDraftOpinionWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "issue-draft-opinion".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(issueDraftOpinionWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_SUPPLEMENT", "SEAL_APPLICATION", "SEALED_UPLOAD", "DELIVERY", "WAIT_FEEDBACK", "COURT_LETTER", "FINAL_OPINION_REVIEW");
        assertThat(issueDraftOpinionWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.sealRequired == true", "form.feedbackDecision == '收到异议'", "form.feedbackDecision == '无异议或未反馈'");
        assertThat(issueDraftOpinionWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "court-letter"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"));
    }

    @Test
    void courtLetterImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest courtLetterForm = formCaptor.getAllValues().stream()
                .filter(request -> "court-letter".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(courtLetterForm.fieldSchemaJson())
                .contains("linkedWorkflowCode", "letterType", "objectionAccepted", "replyDraftCompleted", "departmentDecision", "sealedReplyUploaded", "nextRecommendation");
        assertThat(courtLetterForm.validationSchemaJson())
                .contains("projectReviewPassed == true", "departmentDecision != null", "sealedReplyUploaded == true", "deliveryDate != null");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest courtLetterWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "court-letter".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(courtLetterWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_REGISTER", "ASSISTANT_REPLY", "PROJECT_REVIEW", "DEPARTMENT_REVIEW", "SEAL_APPLICATION",
                        "SEALED_REPLY_UPLOAD", "FINAL_OPINION_REVIEW", "ISSUE_OPINION", "ARCHIVE_SUBFLOW");
        assertThat(courtLetterWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewPassed == true", "form.projectReviewPassed == false",
                        "form.departmentDecision == '进入用章'", "form.departmentDecision == '直接寄送回复函'",
                        "form.departmentDecision == '退回项目负责人'", "form.nextRecommendation == '进入出具鉴定意见书'");
        assertThat(courtLetterWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "issue-opinion"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }

    @Test
    void courtAppearanceImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest courtAppearanceForm = formCaptor.getAllValues().stream()
                .filter(request -> "court-appearance".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(courtAppearanceForm.fieldSchemaJson())
                .contains("linkedWorkflowCode", "courtName", "appearanceFeeRequired", "feeNoticeIssued", "archiveRetrievalRequired",
                        "archiveRetrieved", "appearancePlanPrepared", "appearanceCompleted", "postAppearanceMaterialsUploaded", "nextRecommendation");
        assertThat(courtAppearanceForm.validationSchemaJson())
                .contains("appearanceFeeRequired == true", "archiveRetrievalRequired == true", "appearanceCompleted == true", "archiveConfirmed == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest courtAppearanceWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "court-appearance".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(courtAppearanceWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_REGISTER", "FINANCE_NOTICE", "ARCHIVE_RETRIEVAL", "APPEARANCE_PREPARE",
                        "COURT_APPEARANCE", "POST_APPEARANCE", "NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "ISSUE_OPINION", "ARCHIVE_SUBFLOW");
        assertThat(courtAppearanceWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.appearanceFeeRequired == true", "form.appearanceFeeRequired == false",
                        "form.archiveRetrievalRequired == true", "form.archiveRetrievalRequired == false",
                        "form.appearanceCompleted == true", "form.appearanceCompleted == false",
                        "form.nextRecommendation == '进入出具鉴定意见书'");
        assertThat(courtAppearanceWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "final-opinion-review"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "issue-opinion"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }

    @Test
    void withdrawCaseLetterImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest withdrawForm = formCaptor.getAllValues().stream()
                .filter(request -> "withdraw-case-letter".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(withdrawForm.fieldSchemaJson())
                .contains("linkedWorkflowCode", "withdrawLetterReceivedDate", "withdrawReason", "refundRequired");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest withdrawWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "withdraw-case-letter".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(withdrawWorkflow.nodes()).extracting("nodeCode")
                .contains("LETTER_REGISTER", "PROJECT_DECISION", "REFUND", "TERMINATE_APPRAISAL");
        assertThat(withdrawWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.refundRequired == true", "form.refundRequired == false");
        assertThat(withdrawWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "refund"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void refundImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest refundForm = formCaptor.getAllValues().stream()
                .filter(request -> "refund".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(refundForm.fieldSchemaJson())
                .contains("contractChangeCompleted", "revenueConfirmed", "refundApplicationSubmitted", "paymentCompleted", "paymentVoucherUploaded");
        assertThat(refundForm.validationSchemaJson())
                .contains("paymentCompleted == true", "paymentDate != null");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest refundWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "refund".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(refundWorkflow.nodes()).extracting("nodeCode")
                .contains("PROJECT_PREPARE", "ARCHIVIST_APPLY", "FINANCE_PAYMENT", "TERMINATE_APPRAISAL");
        assertThat(refundWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.paymentCompleted == true", "form.paymentCompleted == false");
        assertThat(refundWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "terminate-appraisal"));
    }

    @Test
    void terminateAppraisalImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest terminateForm = formCaptor.getAllValues().stream()
                .filter(request -> "terminate-appraisal".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(terminateForm.fieldSchemaJson())
                .contains("terminationType", "terminationReason", "draftCompleted", "projectReviewPassed", "sealRequired", "sealedTerminationUploaded", "archiveConfirmed");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest terminateWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "terminate-appraisal".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(terminateWorkflow.nodes()).extracting("nodeCode")
                .contains("ASSISTANT_DRAFT", "PROJECT_REVIEW", "SEAL_APPLICATION", "SEALED_UPLOAD", "ARCHIVE_SUBFLOW");
        assertThat(terminateWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.projectReviewPassed == true", "form.projectReviewPassed == false", "form.archiveConfirmed == true");
        assertThat(terminateWorkflow.transitions()).extracting("transitionConfigJson")
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "seal-application"))
                .anySatisfy(config -> assertThat((String) config).contains("launchSubflow", "archive"));
    }

    @Test
    void archiveImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest archiveForm = formCaptor.getAllValues().stream()
                .filter(request -> "archive".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(archiveForm.fieldSchemaJson())
                .contains("projectArchiveUploaded", "paperScansUploaded", "electronicArchiveLocation", "deliveryRoute", "mailTrackingNo", "centralArchiveApproved", "archiveRoomLocation");
        assertThat(archiveForm.validationSchemaJson())
                .contains("deliveryRoute == '邮寄入库'", "centralArchiveApproved == true");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest archiveWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "archive".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(archiveWorkflow.nodes()).extracting("nodeCode")
                .contains("ARCHIVIST_PREPARE", "MAIL_TRANSFER", "CENTRAL_REVIEW");
        assertThat(archiveWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.deliveryRoute == '邮寄入库'", "form.deliveryRoute == '直接中心审核'",
                        "form.centralArchiveApproved == true", "form.centralArchiveApproved == false");
    }

    @Test
    void sealApplicationImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest sealForm = formCaptor.getAllValues().stream()
                .filter(request -> "seal-application".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(sealForm.fieldSchemaJson())
                .contains("applicationReason", "sealMode", "applicationFilesPrepared", "archivistReviewed", "sealCompleted", "sealedScanUploaded");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest sealWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "seal-application".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(sealWorkflow.nodes()).extracting("nodeCode")
                .contains("APPLICANT_SUBMIT", "ARCHIVIST_REVIEW", "SEAL_OPERATOR", "ARCHIVIST_UPLOAD");
        assertThat(sealWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.archivistReviewed == true", "form.archivistReviewed == false",
                        "form.sealCompleted == true", "form.sealCompleted == false");
    }

    @Test
    void expenseReimbursementImportUsesHighFidelityFormAndWorkflowConfiguration() {
        when(formDesignService.listVersions(any())).thenReturn(List.of());
        when(workflowDesignService.listVersions(any())).thenReturn(List.of());

        service.importCatalog(false);

        ArgumentCaptor<FormDesignRequest> formCaptor = ArgumentCaptor.forClass(FormDesignRequest.class);
        verify(formDesignService, times(19)).saveDraft(formCaptor.capture());
        FormDesignRequest reimbursementForm = formCaptor.getAllValues().stream()
                .filter(request -> "expense-reimbursement".equals(request.formCode()))
                .findFirst()
                .orElseThrow();
        assertThat(reimbursementForm.fieldSchemaJson())
                .contains("expenseSummary", "expenseAmount", "invoiceSummary", "financeProcessed", "financeResult", "paymentDate");
        assertThat(reimbursementForm.validationSchemaJson())
                .contains("financeResult == '已报销'");

        ArgumentCaptor<WorkflowDesignRequest> workflowCaptor = ArgumentCaptor.forClass(WorkflowDesignRequest.class);
        verify(workflowDesignService, times(19)).saveDraft(workflowCaptor.capture());
        WorkflowDesignRequest reimbursementWorkflow = workflowCaptor.getAllValues().stream()
                .filter(request -> "expense-reimbursement".equals(request.wfCode()))
                .findFirst()
                .orElseThrow();
        assertThat(reimbursementWorkflow.nodes()).extracting("nodeCode")
                .contains("INITIATOR_SUBMIT", "FINANCE_PROCESS");
        assertThat(reimbursementWorkflow.transitions()).extracting("conditionExpression")
                .contains("form.financeResult == '已报销'", "form.financeResult == '退回补充'");
    }

}
