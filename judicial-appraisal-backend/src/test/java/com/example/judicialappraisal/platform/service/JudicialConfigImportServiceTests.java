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
}
