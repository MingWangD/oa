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

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final AuthQueryMapper authQueryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final PermissionService permissionService;

    public AuthService(SysUserMapper sysUserMapper,
                       AuthQueryMapper authQueryMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       PermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.authQueryMapper = authQueryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.permissionService = permissionService;
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

    public CurrentUserInfo getCurrentUser(Long userId) {
        SysUser user = requireEnabledUser(userId);
        AuthQueryMapper.CurrentUserBaseRow row = authQueryMapper.selectCurrentUserBaseById(user.getId());
        List<CurrentUserRole> roles = authQueryMapper.selectRolesByUserId(user.getId());
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
