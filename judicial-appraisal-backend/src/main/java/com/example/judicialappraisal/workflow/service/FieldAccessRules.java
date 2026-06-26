package com.example.judicialappraisal.workflow.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class FieldAccessRules {

    private static final Set<String> ENTRUST_REGISTRATION_REQUIRED_FIELDS = Set.of(
            "receivedDate",
            "filingDate",
            "clientName",
            "caseNo",
            "undertakingLegalPerson",
            "institutionSelectionMethod",
            "institutionSelectionTime",
            "appraisalCategory",
            "applicantName",
            "respondentName",
            "urgencyLevel",
            "caseChannel",
            "appraisalMatter"
    );
    private static final Set<String> ENTRUST_REGISTRATION_OPTIONAL_WRITABLE_FIELDS = Set.of(
            "expressNo",
            "departmentHeadId"
    );
    private static final Set<String> ENTRUST_REGISTRATION_BASE_FIELDS = Set.of(
            "expressNo",
            "projectAmount",
            "receivedDate",
            "filingDate",
            "clientName",
            "caseNo",
            "undertakingLegalPerson",
            "institutionSelectionMethod",
            "institutionSelectionTime",
            "appraisalCategory",
            "applicantName",
            "respondentName",
            "urgencyLevel",
            "caseChannel",
            "appraisalMatter"
    );
    private static final Set<String> ENTRUST_ASSIGNMENT_FIELDS = Set.of(
            "entrustAccepted",
            "departmentHeadId",
            "projectLeaderId",
            "projectAssistantId"
    );

    private FieldAccessRules() {
    }

    static boolean isHidden(String fieldName, Map<String, Object> formRule) {
        Map<String, Object> auth = fieldAuth(fieldName, formRule);
        return Boolean.TRUE.equals(toBoolean(auth.get("hidden")));
    }

    static Map<String, Object> withNodeDefaults(String nodeCode, Map<String, Object> formRule) {
        return withNodeDefaults(nodeCode, formRule, null, null, null, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withNodeDefaults(String nodeCode, Map<String, Object> formRule, String assigneeRuleJson, String permissionSchemaJson, List<Map<String, Object>> fields, ObjectMapper objectMapper) {
        Map<String, Object> merged = formRule == null ? new HashMap<>() : new HashMap<>(formRule);
        Map<String, Object> fieldAuth = new HashMap<>();
        Object rawFieldAuth = merged.get("fieldAuth");
        if (rawFieldAuth instanceof Map<?, ?> existingFieldAuth) {
            existingFieldAuth.forEach((key, value) -> {
                Map<String, Object> auth = new HashMap<>();
                if (value instanceof Map<?, ?> existingAuth) {
                    existingAuth.forEach((authKey, authValue) -> auth.put(String.valueOf(authKey), authValue));
                }
                fieldAuth.put(String.valueOf(key), auth);
            });
        }

        // Dynamic fallback for old node rules: fields in permission groups owned by other roles become read-only.
        if (assigneeRuleJson != null && permissionSchemaJson != null) {
            try {
                Map<String, Object> assignee = objectMapper.readValue(assigneeRuleJson, new TypeReference<Map<String, Object>>() {});
                Map<String, Object> permissionSchema = objectMapper.readValue(permissionSchemaJson, new TypeReference<Map<String, Object>>() {});
                
                String roleName = stringValue(assignee.get("roleName"));
                if (roleName == null) roleName = stringValue(assignee.get("roleCode"));
                if (roleName == null) roleName = stringValue(assignee.get("role"));

                Object groupsObj = permissionSchema.get("groups");
                if (groupsObj instanceof Map<?, ?> groups) {
                    Map<String, String> fieldToGroup = new HashMap<>();
                    if (fields != null) {
                        for (Map<String, Object> f : fields) {
                            String group = stringValue(f.get("group"));
                            if (isBlank(group)) {
                                group = "默认组";
                            }
                            String fieldKey = stringValue(f.get("field"));
                            if (isBlank(fieldKey)) {
                                fieldKey = stringValue(f.get("code"));
                            }
                            if (isBlank(fieldKey)) {
                                fieldKey = stringValue(f.get("key"));
                            }
                            if (!isBlank(fieldKey)) {
                                fieldToGroup.put(fieldKey, group);
                            }
                        }
                    }

                    for (Map.Entry<?, ?> groupEntry : groups.entrySet()) {
                        String gName = String.valueOf(groupEntry.getKey());
                        Object gConfigObj = groupEntry.getValue();
                        if (gConfigObj instanceof Map<?, ?> gConfig) {
                            Object rolesObj = gConfig.get("roles");
                            if (rolesObj instanceof List<?> roles) {
                                boolean canEdit = false;
                                if (roleName != null) {
                                    for (Object r : roles) {
                                        String allowedRole = roleValue(r);
                                        if (roleName.equals(allowedRole)) {
                                            canEdit = true;
                                            break;
                                        }
                                    }
                                }

                                if (!canEdit) {
                                    // Set readonly for fields in this group
                                    fieldToGroup.forEach((fKey, fGroup) -> {
                                        if (gName.equals(fGroup)) {
                                            Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent(fKey, k -> new HashMap<>());
                                            if (!auth.containsKey("readonly") && !auth.containsKey("readOnly")) {
                                                auth.put("readonly", true);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Clerk Register Defaults
        String formCode = stringValue(merged.get("formCode"));
        if ("INIT_FILL".equals(nodeCode) || "CLERK_REGISTER".equals(nodeCode)) {
            ENTRUST_REGISTRATION_REQUIRED_FIELDS.forEach(fieldName -> {
                Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent(fieldName, k -> new HashMap<>());
                auth.put("required", true);
                auth.put("readonly", false); // They can always edit these
            });
            ENTRUST_REGISTRATION_OPTIONAL_WRITABLE_FIELDS.forEach(fieldName -> {
                Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent(fieldName, k -> new HashMap<>());
                auth.put("required", "CLERK_REGISTER".equals(nodeCode) && "departmentHeadId".equals(fieldName));
                auth.put("readonly", false);
            });
            Map<String, Object> projectAmountAuth = (Map<String, Object>) fieldAuth.computeIfAbsent("projectAmount", k -> new HashMap<>());
            projectAmountAuth.put("required", false);
            projectAmountAuth.put("readonly", !"INIT_FILL".equals(nodeCode));
            if ("INIT_FILL".equals(nodeCode)) {
                Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent("departmentHeadId", k -> new HashMap<>());
                auth.put("required", false);
                auth.put("hidden", true);
            }
        } else if ("received-entrust".equals(formCode)) {
            ENTRUST_REGISTRATION_BASE_FIELDS.forEach(fieldName -> {
                Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent(fieldName, k -> new HashMap<>());
                auth.put("required", false);
                auth.put("readonly", true);
            });
        }
        ENTRUST_ASSIGNMENT_FIELDS.forEach(fieldName -> {
            Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent(fieldName, k -> new HashMap<>());
            if ("CLERK_REGISTER".equals(nodeCode)) {
                auth.put("readonly", !"departmentHeadId".equals(fieldName));
                auth.put("required", "departmentHeadId".equals(fieldName));
                auth.put("hidden", false);
            } else if ("DEPT_REVIEW".equals(nodeCode)) {
                auth.put("readonly", !("entrustAccepted".equals(fieldName) || "projectLeaderId".equals(fieldName)));
                auth.put("required", "entrustAccepted".equals(fieldName) || "projectLeaderId".equals(fieldName));
                auth.put("hidden", "projectAssistantId".equals(fieldName));
            } else if ("PROJECT_DECISION".equals(nodeCode)) {
                auth.put("readonly", !"projectAssistantId".equals(fieldName));
                auth.put("required", "projectAssistantId".equals(fieldName));
                auth.put("hidden", false);
            } else if (!"INIT_FILL".equals(nodeCode)) {
                auth.putIfAbsent("readonly", true);
            }
        });
        if ("DEPT_REVIEW".equals(nodeCode)) {
            Map<String, Object> auth = (Map<String, Object>) fieldAuth.computeIfAbsent("entrustAccepted", k -> new HashMap<>());
            auth.putIfAbsent("required", true);
        }

        merged.put("fieldAuth", fieldAuth);
        return merged;
    }

    static boolean isRequired(Map<String, Object> field, Map<String, Object> formRule) {
        boolean required = Boolean.TRUE.equals(toBoolean(field.get("required")));
        Map<String, Object> auth = fieldAuth(stringValue(field.get("field")), formRule);
        if (auth.containsKey("required")) {
            required = Boolean.TRUE.equals(toBoolean(auth.get("required")));
        }
        
        // Dynamic formulation: showRequired = required === true && readonly !== true && hidden !== true
        boolean readOnly = isReadOnly(field, formRule);
        boolean hidden = isHidden(stringValue(field.get("field")), formRule);
        
        return required && !readOnly && !hidden;
    }

    static boolean isReadOnly(Map<String, Object> field, Map<String, Object> formRule) {
        boolean readOnly = Boolean.TRUE.equals(toBoolean(field.get("readOnly")))
                || Boolean.TRUE.equals(toBoolean(field.get("readonly")));
        Map<String, Object> auth = fieldAuth(stringValue(field.get("field")), formRule);
        if (auth.containsKey("readonly")) {
            readOnly = Boolean.TRUE.equals(toBoolean(auth.get("readonly")));
        }
        if (auth.containsKey("readOnly")) {
            readOnly = Boolean.TRUE.equals(toBoolean(auth.get("readOnly")));
        }
        return readOnly;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldAuth(String fieldName, Map<String, Object> formRule) {
        if (fieldName == null || fieldName.isBlank() || formRule == null || !formRule.containsKey("fieldAuth")) {
            return Map.of();
        }
        Object rawFieldAuth = formRule.get("fieldAuth");
        if (!(rawFieldAuth instanceof Map<?, ?> fieldAuth)) {
            return Map.of();
        }
        Object rawAuth = fieldAuth.get(fieldName);
        if (!(rawAuth instanceof Map<?, ?> auth)) {
            return Map.of();
        }
        return (Map<String, Object>) auth;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static String roleValue(Object value) {
        if (value instanceof Map<?, ?> role) {
            String roleName = stringValue(role.get("roleName"));
            if (!isBlank(roleName)) {
                return roleName;
            }
            String roleCode = stringValue(role.get("roleCode"));
            if (!isBlank(roleCode)) {
                return roleCode;
            }
            return stringValue(role.get("role"));
        }
        return stringValue(value);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
