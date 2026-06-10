package com.example.judicialappraisal.platform.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.platform.dto.JudicialCatalogDto;
import com.example.judicialappraisal.platform.dto.OaMenuItemDto;
import com.example.judicialappraisal.platform.dto.OaModuleDto;
import com.example.judicialappraisal.platform.dto.ReconstructionPhaseDto;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final PlatformCatalogService platformCatalogService;

    public PlatformController(PlatformCatalogService platformCatalogService) {
        this.platformCatalogService = platformCatalogService;
    }

    @GetMapping("/menus")
    public ApiResponse<List<OaMenuItemDto>> menus() {
        return ApiResponse.success(platformCatalogService.menus());
    }

    @GetMapping("/modules")
    public ApiResponse<List<OaModuleDto>> modules() {
        return ApiResponse.success(platformCatalogService.modules());
    }

    @GetMapping("/judicial-catalog")
    public ApiResponse<JudicialCatalogDto> judicialCatalog() {
        return ApiResponse.success(platformCatalogService.judicialCatalog());
    }

    @GetMapping("/reconstruction-plan")
    public ApiResponse<List<ReconstructionPhaseDto>> reconstructionPlan() {
        return ApiResponse.success(platformCatalogService.reconstructionPlan());
    }
}
