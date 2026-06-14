package com.example.judicialappraisal.organization.dto;

import java.util.List;

public record MenuDto(
    Long id,
    Long parentId,
    String menuName,
    String menuCode,
    String path,
    String component,
    String menuType,
    String icon,
    Integer sortNo,
    List<MenuDto> children
) {}
