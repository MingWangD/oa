package com.example.judicialappraisal.organization.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.auth.service.AuthService;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.organization.dto.AdminRoleDto;
import com.example.judicialappraisal.organization.dto.AdminUserCreateRequest;
import com.example.judicialappraisal.organization.dto.AdminUserDto;
import com.example.judicialappraisal.organization.dto.AdminUserUpdateRequest;
import com.example.judicialappraisal.organization.dto.OrganizationDeptDto;
import com.example.judicialappraisal.organization.dto.OrganizationPostDto;
import com.example.judicialappraisal.organization.dto.UserRoleAssignRequest;
import com.example.judicialappraisal.organization.service.OrganizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class UserController {

    private final OrganizationService organizationService;
    private final AuthService authService;

    public UserController(OrganizationService organizationService, AuthService authService) {
        this.organizationService = organizationService;
        this.authService = authService;
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserDto>> listUsers(@RequestParam(required = false) String keyword,
                                                     Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(organizationService.listUsers(keyword));
    }

    @PostMapping("/users")
    public ApiResponse<AdminUserDto> createUser(@Valid @RequestBody AdminUserCreateRequest request,
                                                Authentication authentication) {
        Long operatorId = requireAdmin(authentication);
        return ApiResponse.success(organizationService.createUser(request, operatorId));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<AdminUserDto> updateUser(@PathVariable Long userId,
                                                @Valid @RequestBody AdminUserUpdateRequest request,
                                                Authentication authentication) {
        Long operatorId = requireAdmin(authentication);
        return ApiResponse.success(organizationService.updateUser(userId, request, operatorId));
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<AdminUserDto> assignUserRoles(@PathVariable Long userId,
                                                     @RequestBody UserRoleAssignRequest request,
                                                     Authentication authentication) {
        requireAdmin(authentication);
        List<Long> roleIds = request == null ? List.of() : request.roleIds();
        return ApiResponse.success(organizationService.assignUserRoles(userId, roleIds));
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminRoleDto>> listRoles(Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(organizationService.listRoles());
    }

    @GetMapping("/depts")
    public ApiResponse<List<OrganizationDeptDto>> listDepts(Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(organizationService.listDepts());
    }

    @GetMapping("/posts")
    public ApiResponse<List<OrganizationPostDto>> listPosts(Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(organizationService.listPosts());
    }

    private Long requireAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo tokenUser)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }

        CurrentUserInfo currentUser = authService.getCurrentUser(tokenUser.id());
        boolean admin = currentUser.roles().stream()
                .map(CurrentUserRole::code)
                .anyMatch(code -> "ADMIN".equalsIgnoreCase(code) || "ROLE_ADMIN".equalsIgnoreCase(code));
        if (!admin) {
            throw new AccessDeniedException("Admin role required");
        }
        return currentUser.id();
    }
}
