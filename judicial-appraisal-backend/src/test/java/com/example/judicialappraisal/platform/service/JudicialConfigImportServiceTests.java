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
}
