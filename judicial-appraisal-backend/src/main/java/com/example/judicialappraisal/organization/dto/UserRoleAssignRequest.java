package com.example.judicialappraisal.organization.dto;

import java.util.List;

public record UserRoleAssignRequest(List<Long> roleIds) {
}
