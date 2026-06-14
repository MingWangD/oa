package com.example.judicialappraisal.workflow.design;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.workflow.design.dto.FormVersionDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FormDesignServiceTests {

    private final FormDefinitionMapper formDefinitionMapper = mock(FormDefinitionMapper.class);
    private final FormVersionMapper formVersionMapper = mock(FormVersionMapper.class);
    private final FormDesignService service = new FormDesignService(formDefinitionMapper, formVersionMapper);

    @Test
    void publishClonesDraftAsNextImmutableVersion() {
        FormDefinition definition = new FormDefinition();
        definition.setId(7L);
        definition.setFormCode("final-opinion-review");
        definition.setFormName("鉴定意见书送审稿编制");
        definition.setCurrentPublishedVersion(1);

        FormVersion draft = new FormVersion();
        draft.setId(11L);
        draft.setFormId(7L);
        draft.setFormCode("final-opinion-review");
        draft.setFormName("鉴定意见书送审稿编制");
        draft.setVersionNo(0);
        draft.setStatus("draft");
        draft.setFieldSchemaJson("[{\"field\":\"opinionDraft\"}]");

        FormVersion latest = new FormVersion();
        latest.setId(10L);
        latest.setFormId(7L);
        latest.setFormCode("final-opinion-review");
        latest.setFormName("鉴定意见书送审稿编制");
        latest.setVersionNo(1);
        latest.setStatus("published");

        when(formVersionMapper.selectOne(any())).thenReturn(draft, latest);
        when(formDefinitionMapper.selectOne(any())).thenReturn(definition);

        FormVersionDto result = service.publish("final-opinion-review");

        ArgumentCaptor<FormVersion> captor = ArgumentCaptor.forClass(FormVersion.class);
        verify(formVersionMapper).insert(captor.capture());
        FormVersion inserted = captor.getValue();
        assertThat(inserted.getVersionNo()).isEqualTo(2);
        assertThat(inserted.getStatus()).isEqualTo("published");
        assertThat(inserted.getImmutableFlag()).isEqualTo(1);
        assertThat(inserted.getSourceVersionId()).isEqualTo(11L);
        assertThat(result.versionNo()).isEqualTo(2);
    }
}
