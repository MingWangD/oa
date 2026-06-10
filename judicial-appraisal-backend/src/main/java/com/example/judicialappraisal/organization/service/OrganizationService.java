package com.example.judicialappraisal.organization.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.organization.dto.AdminRoleDto;
import com.example.judicialappraisal.organization.dto.AdminUserCreateRequest;
import com.example.judicialappraisal.organization.dto.AdminUserDto;
import com.example.judicialappraisal.organization.dto.AdminUserUpdateRequest;
import com.example.judicialappraisal.organization.dto.OrganizationDeptDto;
import com.example.judicialappraisal.organization.dto.OrganizationPostDto;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUser;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.AdminQueryMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrganizationService extends ServiceImpl<SysUserMapper, SysUser> {

    private final AdminQueryMapper adminQueryMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(AdminQueryMapper adminQueryMapper,
                               SysRoleMapper sysRoleMapper,
                               SysUserRoleMapper sysUserRoleMapper,
                               PasswordEncoder passwordEncoder) {
        this.adminQueryMapper = adminQueryMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminUserDto> listUsers(String keyword) {
        List<AdminQueryMapper.AdminUserRow> users = adminQueryMapper.selectUsers(trimToNull(keyword));
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(AdminQueryMapper.AdminUserRow::id).toList();
        Map<Long, List<AdminRoleDto>> rolesByUserId = adminQueryMapper.selectRolesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        AdminQueryMapper.UserRoleRow::userId,
                        HashMap::new,
                        Collectors.mapping(this::toRoleDto, Collectors.toList())
                ));

        return users.stream()
                .map(user -> new AdminUserDto(
                        user.id(),
                        user.username(),
                        user.realName(),
                        user.mobile(),
                        user.email(),
                        user.deptId(),
                        user.deptName(),
                        user.postId(),
                        user.postName(),
                        user.status(),
                        rolesByUserId.getOrDefault(user.id(), List.of())
                ))
                .toList();
    }

    public List<AdminRoleDto> listRoles() {
        return adminQueryMapper.selectRoles().stream()
                .map(this::toRoleDto)
                .toList();
    }

    @Transactional
    public AdminUserDto createUser(AdminUserCreateRequest request, Long operatorId) {
        String username = requireTrimmed(request.username(), "Username is required");
        if (getBaseMapper().selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(409, "Username already exists");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(requireTrimmed(request.realName(), "Real name is required"));
        user.setMobile(trimToNull(request.mobile()));
        user.setEmail(trimToNull(request.email()));
        user.setDeptId(request.deptId());
        user.setPostId(request.postId());
        user.setStatus(normalizeStatus(request.status()));
        user.setCreatedBy(operatorId);
        user.setUpdatedBy(operatorId);
        getBaseMapper().insert(user);

        assignRoles(user.getId(), request.roleIds());
        return getUserDto(user.getId());
    }

    @Transactional
    public AdminUserDto updateUser(Long userId, AdminUserUpdateRequest request, Long operatorId) {
        requireUser(userId);
        getBaseMapper().update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getDeleted, 0)
                .set(SysUser::getRealName, requireTrimmed(request.realName(), "Real name is required"))
                .set(SysUser::getMobile, trimToNull(request.mobile()))
                .set(SysUser::getEmail, trimToNull(request.email()))
                .set(SysUser::getDeptId, request.deptId())
                .set(SysUser::getPostId, request.postId())
                .set(SysUser::getStatus, normalizeStatus(request.status()))
                .set(SysUser::getUpdatedBy, operatorId));
        return getUserDto(userId);
    }

    @Transactional
    public AdminUserDto assignUserRoles(Long userId, List<Long> roleIds) {
        requireUser(userId);
        assignRoles(userId, roleIds);
        return getUserDto(userId);
    }

    public List<OrganizationDeptDto> listDepts() {
        return adminQueryMapper.selectDepts();
    }

    public List<OrganizationPostDto> listPosts() {
        return adminQueryMapper.selectPosts();
    }

    private void assignRoles(Long userId, List<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        if (!normalizedRoleIds.isEmpty()) {
            Long matchedCount = sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getId, normalizedRoleIds)
                    .eq(SysRole::getDeleted, 0));
            if (matchedCount != normalizedRoleIds.size()) {
                throw new BusinessException("One or more roles do not exist");
            }
        }

        sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        for (Long roleId : normalizedRoleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            sysUserRoleMapper.insert(userRole);
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = getBaseMapper().selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    private AdminUserDto getUserDto(Long userId) {
        return listUsers(null).stream()
                .filter(user -> Objects.equals(user.id(), userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "User not found"));
    }

    private AdminRoleDto toRoleDto(AdminQueryMapper.RoleRow role) {
        return new AdminRoleDto(role.id(), role.roleCode(), role.roleName(), role.status());
    }

    private AdminRoleDto toRoleDto(AdminQueryMapper.UserRoleRow role) {
        return new AdminRoleDto(role.id(), role.roleCode(), role.roleName(), role.status());
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return "enabled";
        }
        if ("enabled".equalsIgnoreCase(value) || "disabled".equalsIgnoreCase(value)) {
            return value.toLowerCase();
        }
        throw new BusinessException("Unsupported user status");
    }

    private String requireTrimmed(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
