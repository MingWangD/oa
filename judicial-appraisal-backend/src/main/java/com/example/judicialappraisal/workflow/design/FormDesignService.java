package com.example.judicialappraisal.workflow.design;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.design.dto.FormDefinitionDto;
import com.example.judicialappraisal.workflow.design.dto.FormDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.FormVersionDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormDesignService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";

    private final FormDefinitionMapper formDefinitionMapper;
    private final FormVersionMapper formVersionMapper;

    public FormDesignService(FormDefinitionMapper formDefinitionMapper, FormVersionMapper formVersionMapper) {
        this.formDefinitionMapper = formDefinitionMapper;
        this.formVersionMapper = formVersionMapper;
    }

    public List<FormDefinitionDto> listDefinitions() {
        return formDefinitionMapper.selectList(new LambdaQueryWrapper<FormDefinition>()
                        .eq(FormDefinition::getEnabled, 1)
                        .orderByAsc(FormDefinition::getFormCode))
                .stream()
                .map(this::toDefinitionDto)
                .toList();
    }

    public List<FormVersionDto> listVersions(String formCode) {
        return formVersionMapper.selectList(new LambdaQueryWrapper<FormVersion>()
                        .eq(FormVersion::getFormCode, formCode)
                        .eq(FormVersion::getDeleted, 0)
                        .orderByDesc(FormVersion::getVersionNo)
                        .orderByDesc(FormVersion::getId))
                .stream()
                .map(this::toVersionDto)
                .toList();
    }

    public FormVersionDto getDraft(String formCode) {
        FormVersion formVersion = requireDraft(formCode);
        return toVersionDto(formVersion);
    }

    public FormVersionDto preview(String formCode) {
        FormVersion published = latestPublished(formCode);
        if (published != null) {
            return toVersionDto(published);
        }
        return toVersionDto(requireDraft(formCode));
    }

    @Transactional
    public FormVersionDto saveDraft(FormDesignRequest request) {
        validateRequest(request);
        FormDefinition definition = ensureDefinition(request.formCode(), request.formName(), request.category());
        FormVersion draft = requireOrCreateDraft(definition, request);
        fillDraft(draft, definition, request);
        if (draft.getId() == null) {
            formVersionMapper.insert(draft);
        } else {
            formVersionMapper.updateById(draft);
        }
        return toVersionDto(draft);
    }

    @Transactional
    public FormVersionDto publish(String formCode) {
        FormVersion draft = requireDraft(formCode);
        FormDefinition definition = requireDefinition(formCode);
        int nextVersion = nextPublishedVersion(formCode);
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();

        FormVersion published = cloneVersion(draft);
        published.setId(null);
        published.setFormId(definition.getId());
        published.setVersionNo(nextVersion);
        published.setStatus(STATUS_PUBLISHED);
        published.setSourceVersionId(draft.getId());
        published.setPublishedBy(userId);
        published.setPublishedTime(now);
        published.setImmutableFlag(1);
        published.setCreatedBy(userId);
        published.setUpdatedBy(userId);
        published.setCreatedTime(now);
        published.setUpdatedTime(now);
        formVersionMapper.insert(published);

        definition.setCurrentPublishedVersion(nextVersion);
        definition.setUpdatedBy(userId);
        definition.setUpdatedTime(now);
        formDefinitionMapper.updateById(definition);
        return toVersionDto(published);
    }

    @Transactional
    public FormVersionDto restore(String formCode, Integer versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw new BusinessException("版本号不能为空");
        }
        FormVersion source = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, formCode)
                .eq(FormVersion::getVersionNo, versionNo)
                .eq(FormVersion::getStatus, STATUS_PUBLISHED)
                .eq(FormVersion::getDeleted, 0));
        if (source == null) {
            throw new BusinessException("版本不存在");
        }
        FormDefinition definition = requireDefinition(formCode);
        deleteExistingDraft(definition.getFormCode());

        FormVersion draft = cloneVersion(source);
        draft.setId(null);
        draft.setFormId(definition.getId());
        draft.setVersionNo(0);
        draft.setStatus(STATUS_DRAFT);
        draft.setSourceVersionId(source.getId());
        draft.setPublishedBy(null);
        draft.setPublishedTime(null);
        draft.setImmutableFlag(0);
        draft.setCreatedBy(currentUserId());
        draft.setUpdatedBy(currentUserId());
        draft.setCreatedTime(LocalDateTime.now());
        draft.setUpdatedTime(LocalDateTime.now());
        formVersionMapper.insert(draft);
        return toVersionDto(draft);
    }

    public FormDefinitionDto getDefinition(String formCode) {
        return toDefinitionDto(requireDefinition(formCode));
    }

    private void validateRequest(FormDesignRequest request) {
        if (request.formCode() == null || request.formCode().isBlank()) {
            throw new BusinessException("表单编码不能为空");
        }
        if (request.formName() == null || request.formName().isBlank()) {
            throw new BusinessException("表单名称不能为空");
        }
    }

    private FormDefinition ensureDefinition(String formCode, String formName, String category) {
        FormDefinition definition = requireDefinitionOrNull(formCode);
        if (definition == null) {
            definition = new FormDefinition();
            definition.setFormCode(formCode);
            definition.setFormName(formName);
            definition.setCategory(category);
            definition.setCurrentPublishedVersion(0);
            definition.setEnabled(1);
            definition.setCreatedBy(currentUserId());
            definition.setUpdatedBy(currentUserId());
            definition.setCreatedTime(LocalDateTime.now());
            definition.setUpdatedTime(LocalDateTime.now());
            formDefinitionMapper.insert(definition);
            return definition;
        }
        definition.setFormName(formName);
        definition.setCategory(category);
        definition.setUpdatedBy(currentUserId());
        definition.setUpdatedTime(LocalDateTime.now());
        formDefinitionMapper.updateById(definition);
        return definition;
    }

    private FormDefinition requireDefinition(String formCode) {
        FormDefinition definition = requireDefinitionOrNull(formCode);
        if (definition == null) {
            throw new BusinessException("表单不存在");
        }
        return definition;
    }

    private FormDefinition requireDefinitionOrNull(String formCode) {
        return formDefinitionMapper.selectOne(new LambdaQueryWrapper<FormDefinition>()
                .eq(FormDefinition::getFormCode, formCode)
                .eq(FormDefinition::getDeleted, 0)
                .last("limit 1"));
    }

    private FormVersion requireDraft(String formCode) {
        FormVersion draft = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, formCode)
                .eq(FormVersion::getStatus, STATUS_DRAFT)
                .eq(FormVersion::getDeleted, 0)
                .last("limit 1"));
        if (draft == null) {
            throw new BusinessException("草稿不存在");
        }
        return draft;
    }

    private FormVersion latestPublished(String formCode) {
        return formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, formCode)
                .eq(FormVersion::getStatus, STATUS_PUBLISHED)
                .eq(FormVersion::getDeleted, 0)
                .orderByDesc(FormVersion::getVersionNo)
                .last("limit 1"));
    }

    private int nextPublishedVersion(String formCode) {
        FormVersion latest = latestPublished(formCode);
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private FormVersion requireOrCreateDraft(FormDefinition definition, FormDesignRequest request) {
        FormVersion draft = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, definition.getFormCode())
                .eq(FormVersion::getStatus, STATUS_DRAFT)
                .eq(FormVersion::getDeleted, 0)
                .last("limit 1"));
        if (draft == null) {
            draft = new FormVersion();
            draft.setFormId(definition.getId());
            draft.setFormCode(definition.getFormCode());
            draft.setFormName(request.formName());
            draft.setVersionNo(0);
            draft.setStatus(STATUS_DRAFT);
            draft.setCreatedBy(currentUserId());
            draft.setUpdatedBy(currentUserId());
            draft.setCreatedTime(LocalDateTime.now());
            draft.setUpdatedTime(LocalDateTime.now());
            draft.setImmutableFlag(0);
        }
        return draft;
    }

    private void deleteExistingDraft(String formCode) {
        formVersionMapper.delete(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, formCode)
                .eq(FormVersion::getStatus, STATUS_DRAFT));
    }

    private void fillDraft(FormVersion draft, FormDefinition definition, FormDesignRequest request) {
        draft.setFormId(definition.getId());
        draft.setFormCode(definition.getFormCode());
        draft.setFormName(request.formName());
        draft.setStatus(STATUS_DRAFT);
        draft.setVersionNo(0);
        draft.setInputFilesJson(request.inputFilesJson());
        draft.setOutputFilesJson(request.outputFilesJson());
        draft.setVersionedArtifactsJson(request.versionedArtifactsJson());
        draft.setFieldSchemaJson(request.fieldSchemaJson());
        draft.setLayoutSchemaJson(request.layoutSchemaJson());
        draft.setValidationSchemaJson(request.validationSchemaJson());
        draft.setPermissionSchemaJson(request.permissionSchemaJson());
        draft.setLinkageSchemaJson(request.linkageSchemaJson());
        draft.setCalculationSchemaJson(request.calculationSchemaJson());
        draft.setAttachmentSchemaJson(request.attachmentSchemaJson());
        draft.setSubtableSchemaJson(request.subtableSchemaJson());
        draft.setNotesJson(request.notesJson());
        draft.setImmutableFlag(0);
        draft.setPublishedBy(null);
        draft.setPublishedTime(null);
        draft.setSourceVersionId(draft.getSourceVersionId());
        draft.setUpdatedBy(currentUserId());
        draft.setUpdatedTime(LocalDateTime.now());
    }

    private FormVersion cloneVersion(FormVersion source) {
        FormVersion clone = new FormVersion();
        clone.setFormId(source.getFormId());
        clone.setFormCode(source.getFormCode());
        clone.setFormName(source.getFormName());
        clone.setVersionNo(source.getVersionNo());
        clone.setStatus(source.getStatus());
        clone.setInputFilesJson(source.getInputFilesJson());
        clone.setOutputFilesJson(source.getOutputFilesJson());
        clone.setVersionedArtifactsJson(source.getVersionedArtifactsJson());
        clone.setFieldSchemaJson(source.getFieldSchemaJson());
        clone.setLayoutSchemaJson(source.getLayoutSchemaJson());
        clone.setValidationSchemaJson(source.getValidationSchemaJson());
        clone.setPermissionSchemaJson(source.getPermissionSchemaJson());
        clone.setLinkageSchemaJson(source.getLinkageSchemaJson());
        clone.setCalculationSchemaJson(source.getCalculationSchemaJson());
        clone.setAttachmentSchemaJson(source.getAttachmentSchemaJson());
        clone.setSubtableSchemaJson(source.getSubtableSchemaJson());
        clone.setNotesJson(source.getNotesJson());
        return clone;
    }

    private FormDefinitionDto toDefinitionDto(FormDefinition definition) {
        return new FormDefinitionDto(
                definition.getId(),
                definition.getFormCode(),
                definition.getFormName(),
                definition.getCategory(),
                definition.getCurrentPublishedVersion(),
                definition.getEnabled(),
                definition.getCreatedTime(),
                definition.getUpdatedTime()
        );
    }

    private FormVersionDto toVersionDto(FormVersion version) {
        return new FormVersionDto(
                version.getId(),
                version.getFormId(),
                version.getFormCode(),
                version.getFormName(),
                version.getVersionNo(),
                version.getStatus(),
                version.getInputFilesJson(),
                version.getOutputFilesJson(),
                version.getVersionedArtifactsJson(),
                version.getFieldSchemaJson(),
                version.getLayoutSchemaJson(),
                version.getValidationSchemaJson(),
                version.getPermissionSchemaJson(),
                version.getLinkageSchemaJson(),
                version.getCalculationSchemaJson(),
                version.getAttachmentSchemaJson(),
                version.getSubtableSchemaJson(),
                version.getNotesJson(),
                version.getSourceVersionId(),
                version.getPublishedBy(),
                version.getPublishedTime(),
                version.getImmutableFlag(),
                version.getCreatedTime(),
                version.getUpdatedTime()
        );
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }
        return userInfo.id();
    }
}
