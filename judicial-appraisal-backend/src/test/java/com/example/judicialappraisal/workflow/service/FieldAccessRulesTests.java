package com.example.judicialappraisal.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FieldAccessRulesTests {

    @Test
    void nodeReadonlyFalseOverridesBaseReadonlyField() {
        Map<String, Object> field = Map.of(
                "field", "projectLeaderId",
                "required", true,
                "readOnly", true
        );
        Map<String, Object> formRule = Map.of(
                "fieldAuth", Map.of(
                        "projectLeaderId", Map.of("readonly", false)
                )
        );

        assertThat(FieldAccessRules.isReadOnly(field, formRule)).isFalse();
        assertThat(FieldAccessRules.isRequired(field, formRule)).isTrue();
    }

    @Test
    void nodeRequiredTrueOverridesBaseOptionalField() {
        Map<String, Object> field = Map.of(
                "field", "entrustAccepted",
                "required", false,
                "readOnly", false
        );
        Map<String, Object> formRule = Map.of(
                "fieldAuth", Map.of(
                        "entrustAccepted", Map.of("required", true)
                )
        );

        assertThat(FieldAccessRules.isRequired(field, formRule)).isTrue();
        assertThat(FieldAccessRules.isReadOnly(field, formRule)).isFalse();
    }

    @Test
    void hiddenFieldIsDetectedFromNodeRule() {
        Map<String, Object> formRule = Map.of(
                "fieldAuth", Map.of(
                        "departmentHeadId", Map.of("hidden", true)
                )
        );

        assertThat(FieldAccessRules.isHidden("departmentHeadId", formRule)).isTrue();
        assertThat(FieldAccessRules.isHidden("projectLeaderId", formRule)).isFalse();
    }

    @Test
    void deptReviewDefaultsEntrustDecisionToRequiredForExistingImportedData() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults("DEPT_REVIEW", Map.of("formCode", "received-entrust"));
        Map<String, Object> field = Map.of(
                "field", "entrustAccepted",
                "required", false,
                "readOnly", false
        );

        assertThat(FieldAccessRules.isRequired(field, formRule)).isTrue();
    }

    @Test
    void initFillDefaultsRegistrationFieldsToRequiredForExistingImportedData() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults("INIT_FILL", Map.of("formCode", "received-entrust"));

        assertThat(FieldAccessRules.isRequired(optionalField("caseNo"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("clientName"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("expressNo"), formRule)).isFalse();
        assertThat(FieldAccessRules.isRequired(optionalField("filingDate"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("undertakingLegalPerson"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("institutionSelectionMethod"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("institutionSelectionTime"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("appraisalCategory"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("applicantName"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("respondentName"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("appraisalMatter"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("projectNo"), formRule)).isFalse();
    }

    @Test
    void clerkRegisterDefaultsRegistrationFieldsToRequiredForExistingImportedData() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults("CLERK_REGISTER", Map.of("formCode", "received-entrust"));

        assertThat(FieldAccessRules.isRequired(optionalField("caseNo"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("clientName"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("urgencyLevel"), formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(optionalField("caseChannel"), formRule)).isTrue();
    }

    @Test
    void permissionSchemaMarksOtherRoleFieldsReadonlyAndNotRequired() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults(
                "CLERK_REGISTER",
                Map.of("formCode", "received-entrust"),
                "{\"roleName\":\"收案员\"}",
                "{\"groups\":{\"部门负责人意见\":{\"roles\":[\"部门负责人\"]}}}",
                List.of(Map.of("field", "entrustAccepted", "group", "部门负责人意见")),
                new ObjectMapper()
        );
        Map<String, Object> field = Map.of(
                "field", "entrustAccepted",
                "required", true,
                "readOnly", false
        );

        assertThat(FieldAccessRules.isReadOnly(field, formRule)).isTrue();
        assertThat(FieldAccessRules.isRequired(field, formRule)).isFalse();
    }

    @Test
    void permissionSchemaKeepsCurrentRoleFieldsWritable() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults(
                "DEPT_REVIEW",
                Map.of("formCode", "received-entrust"),
                "{\"roleName\":\"部门负责人\"}",
                "{\"groups\":{\"部门负责人意见\":{\"roles\":[\"部门负责人\"]}}}",
                List.of(Map.of("field", "entrustAccepted", "group", "部门负责人意见")),
                new ObjectMapper()
        );
        Map<String, Object> field = Map.of(
                "field", "entrustAccepted",
                "required", false,
                "readOnly", false
        );

        assertThat(FieldAccessRules.isReadOnly(field, formRule)).isFalse();
        assertThat(FieldAccessRules.isRequired(field, formRule)).isTrue();
    }

    @Test
    void permissionSchemaDoesNotOverwriteExplicitReadonlyConfig() {
        Map<String, Object> formRule = FieldAccessRules.withNodeDefaults(
                "CLERK_REGISTER",
                Map.of("fieldAuth", Map.of("entrustAccepted", Map.of("readonly", false))),
                "{\"roleName\":\"收案员\"}",
                "{\"groups\":{\"部门负责人意见\":{\"roles\":[\"部门负责人\"]}}}",
                List.of(Map.of("field", "entrustAccepted", "group", "部门负责人意见")),
                new ObjectMapper()
        );

        assertThat(FieldAccessRules.isReadOnly(optionalField("entrustAccepted"), formRule)).isFalse();
    }

    private Map<String, Object> optionalField(String fieldName) {
        return Map.of(
                "field", fieldName,
                "required", false,
                "readOnly", false
        );
    }
}
