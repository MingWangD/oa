package com.example.judicialappraisal.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.judicialappraisal.auth.dto.ChangePasswordRequest;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.auth.dto.LoginRequest;
import com.example.judicialappraisal.auth.dto.LoginResponse;
import com.example.judicialappraisal.auth.mapper.AuthQueryMapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.organization.entity.SysUser;
import com.example.judicialappraisal.organization.mapper.SysUserMapper;
import com.example.judicialappraisal.organization.service.PermissionService;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.judicialappraisal.auth.dto.RegisterRequest;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final AuthQueryMapper authQueryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final PermissionService permissionService;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public AuthService(SysUserMapper sysUserMapper,
                       AuthQueryMapper authQueryMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       PermissionService permissionService,
                       SysRoleMapper sysRoleMapper,
                       SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.authQueryMapper = authQueryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.permissionService = permissionService;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null
                || !StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!"enabled".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User is disabled");
        }

        CurrentUserInfo userInfo = getCurrentUser(user.getId());
        return new LoginResponse(jwtTokenService.generateToken(userInfo), userInfo);
    }

    @Transactional
    public void register(RegisterRequest request) {
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在 (Username already exists)");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setStatus("enabled");
        user.setCreatedTime(LocalDateTime.now());
        sysUserMapper.insert(user);

        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, "APPLICANT")
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException("系统未配置默认的申请人角色 (APPLICANT)，请联系管理员");
        }

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);
    }

    public CurrentUserInfo getCurrentUser(Long userId) {
        SysUser user = requireEnabledUser(userId);
        AuthQueryMapper.CurrentUserBaseRow row = authQueryMapper.selectCurrentUserBaseById(user.getId());
        List<CurrentUserRole> roles = authQueryMapper.selectRolesByUserId(user.getId()).stream()
                .map(role -> new CurrentUserRole(
                        role.id(),
                        role.code(),
                        role.name(),
                        role.dataScope(),
                        "custom".equalsIgnoreCase(role.dataScope())
                                ? authQueryMapper.selectCustomDeptIdsByRoleId(role.id())
                                : List.of()
                ))
                .toList();
        Set<String> permissions = permissionService.getPermissionsByUserId(user.getId());

        if (row == null) {
            return new CurrentUserInfo(
                    user.getId(),
                    user.getUsername(),
                    user.getRealName(),
                    null,
                    null,
                    user.getDeptId(),
                    null,
                    user.getPostId(),
                    null,
                    user.getStatus(),
                    roles,
                    permissions
            );
        }

        return new CurrentUserInfo(
                row.id(),
                row.username(),
                row.realName(),
                row.mobile(),
                row.email(),
                row.deptId(),
                row.deptName(),
                row.postId(),
                row.postName(),
                row.status(),
                roles,
                permissions
        );
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("New password confirmation does not match");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("New password must be different from the current password");
        }

        SysUser user = requireEnabledUser(userId);
        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        sysUserMapper.updateById(user);
    }

    private SysUser requireEnabledUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("User not found");
        }
        if (!"enabled".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User is disabled");
        }
        return user;
    }
}
