package com.example.judicialappraisal.platform.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.service.PermissionService;
import com.example.judicialappraisal.platform.dto.JudicialCatalogDto;
import com.example.judicialappraisal.platform.dto.JudicialConfigImportResult;
import com.example.judicialappraisal.platform.dto.OaMenuItemDto;
import com.example.judicialappraisal.platform.dto.OaModuleDto;
import com.example.judicialappraisal.platform.dto.ReconstructionPhaseDto;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final PlatformCatalogService platformCatalogService;
    private final PermissionService permissionService;
    private final JudicialConfigImportService judicialConfigImportService;

    public PlatformController(PlatformCatalogService platformCatalogService,
                              PermissionService permissionService,
                              JudicialConfigImportService judicialConfigImportService) {
        this.platformCatalogService = platformCatalogService;
        this.permissionService = permissionService;
        this.judicialConfigImportService = judicialConfigImportService;
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuDto>> menus(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(permissionService.getMenusByUserId(userInfo.id()));
    }

    @GetMapping("/modules")
    public ApiResponse<List<OaModuleDto>> modules() {
        return ApiResponse.success(platformCatalogService.modules());
    }

    @GetMapping("/judicial-catalog")
    public ApiResponse<JudicialCatalogDto> judicialCatalog() {
        return ApiResponse.success(platformCatalogService.judicialCatalog());
    }

    @PostMapping("/judicial-catalog/import")
    public ApiResponse<JudicialConfigImportResult> importJudicialCatalog(@RequestParam(defaultValue = "false") boolean forceNewVersion) {
        return ApiResponse.success(judicialConfigImportService.importCatalog(forceNewVersion));
    }

    @GetMapping("/reconstruction-plan")
    public ApiResponse<List<ReconstructionPhaseDto>> reconstructionPlan() {
        return ApiResponse.success(platformCatalogService.reconstructionPlan());
    }
}
