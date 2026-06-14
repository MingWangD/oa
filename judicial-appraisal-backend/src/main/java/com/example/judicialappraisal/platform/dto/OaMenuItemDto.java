package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record OaMenuItemDto(
        String code,
        String title,
        String path,
        String module,
        String capabilityStatus,
        Integer sortNo,
        List<OaMenuItemDto> children
) {
    public OaMenuItemDto {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
