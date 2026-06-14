package com.example.judicialappraisal.workflow.design;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.workflow.design.dto.FormVersionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forms")
public class FormRuntimeController {

    private final FormDesignService formDesignService;

    public FormRuntimeController(FormDesignService formDesignService) {
        this.formDesignService = formDesignService;
    }

    @GetMapping("/{formCode}/preview")
    public ApiResponse<FormVersionDto> formPreview(@PathVariable String formCode) {
        return ApiResponse.success(formDesignService.preview(formCode));
    }
}
