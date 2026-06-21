package com.example.judicialappraisal.organization.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.auth.service.AuthService;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.organization.dto.AdminRoleDto;
import com.example.judicialappraisal.organization.dto.AdminUserCreateRequest;
import com.example.judicialappraisal.organization.dto.AdminUserDto;
import com.example.judicialappraisal.organization.dto.AdminUserUpdateRequest;
import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.dto.OrganizationDeptDto;
import com.example.judicialappraisal.organization.dto.OrganizationPostDto;
import com.example.judicialappraisal.organization.dto.RoleDataScopeUpdateRequest;
import com.example.judicialappraisal.organization.dto.RoleMenuAssignRequest;
import com.example.judicialappraisal.organization.dto.UserRoleAssignRequest;
import com.example.judicialappraisal.organization.service.OrganizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminUserDto>> listUsers(@RequestParam(required = false) String keyword,
                                                     Authentication authentication) {
        return ApiResponse.success(organizationService.listUsers(keyword));
    }

    @GetMapping("/users/options")
    public ApiResponse<List<AdminUserDto>> listUserOptions() {
        return ApiResponse.success(organizationService.listUsers(null));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserDto> createUser(@Valid @RequestBody AdminUserCreateRequest request,
                                                Authentication authentication) {
        CurrentUserInfo currentUser = (CurrentUserInfo) authentication.getPrincipal();
        return ApiResponse.success(organizationService.createUser(request, currentUser.id()));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserDto> updateUser(@PathVariable Long userId,
                                                @Valid @RequestBody AdminUserUpdateRequest request,
                                                Authentication authentication) {
        CurrentUserInfo currentUser = (CurrentUserInfo) authentication.getPrincipal();
        return ApiResponse.success(organizationService.updateUser(userId, request, currentUser.id()));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserDto> assignUserRoles(@PathVariable Long userId,
                                                     @RequestBody UserRoleAssignRequest request,
                                                     Authentication authentication) {
        List<Long> roleIds = request == null ? List.of() : request.roleIds();
        return ApiResponse.success(organizationService.assignUserRoles(userId, roleIds));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminRoleDto>> listRoles(Authentication authentication) {
        return ApiResponse.success(organizationService.listRoles());
    }

    @PutMapping("/roles/{roleId}/data-scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminRoleDto> updateRoleDataScope(@PathVariable Long roleId,
                                                         @Valid @RequestBody RoleDataScopeUpdateRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(organizationService.updateRoleDataScope(roleId, request));
    }

    @GetMapping("/depts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<OrganizationDeptDto>> listDepts(Authentication authentication) {
        return ApiResponse.success(organizationService.listDepts());
    }

    @GetMapping("/posts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<OrganizationPostDto>> listPosts(Authentication authentication) {
        return ApiResponse.success(organizationService.listPosts());
    }

    @GetMapping("/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MenuDto>> listMenus(Authentication authentication) {
        return ApiResponse.success(organizationService.listAllMenus());
    }

    @GetMapping("/roles/{roleId}/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Long>> getRoleMenuIds(@PathVariable Long roleId,
                                                   Authentication authentication) {
        return ApiResponse.success(organizationService.getRoleMenuIds(roleId));
    }

    @PutMapping("/roles/{roleId}/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> assignRoleMenus(@PathVariable Long roleId,
                                             @RequestBody RoleMenuAssignRequest request,
                                             Authentication authentication) {
        List<Long> menuIds = request == null ? List.of() : request.menuIds();
        organizationService.assignRoleMenus(roleId, menuIds);
        return ApiResponse.success();
    }
}
