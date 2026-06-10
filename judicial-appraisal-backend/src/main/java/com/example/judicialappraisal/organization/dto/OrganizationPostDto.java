package com.example.judicialappraisal.organization.dto;

public record OrganizationPostDto(
        Long id,
        String postName,
        String postCode,
        Integer sortNo,
        String status) {
}
