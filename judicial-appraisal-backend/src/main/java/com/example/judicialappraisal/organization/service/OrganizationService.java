package com.example.judicialappraisal.organization.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.organization.dto.AdminRoleDto;
import com.example.judicialappraisal.organization.dto.AdminUserCreateRequest;
import com.example.judicialappraisal.organization.dto.AdminUserDto;
import com.example.judicialappraisal.organization.dto.AdminUserUpdateRequest;
import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.dto.RoleDataScopeUpdateRequest;
import com.example.judicialappraisal.organization.dto.OrganizationDeptDto;
import com.example.judicialappraisal.organization.dto.OrganizationPostDto;
import com.example.judicialappraisal.organization.entity.SysMenu;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysRoleMenu;
import com.example.judicialappraisal.organization.entity.SysUser;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.AdminQueryMapper;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleDataScopeDeptMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMenuMapper;
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
    private final SysMenuMapper sysMenuMapper;
    private final SysRoleDataScopeDeptMapper sysRoleDataScopeDeptMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(AdminQueryMapper adminQueryMapper,
                               SysRoleMapper sysRoleMapper,
                               SysMenuMapper sysMenuMapper,
                               SysRoleDataScopeDeptMapper sysRoleDataScopeDeptMapper,
                               SysUserRoleMapper sysUserRoleMapper,
                               SysRoleMenuMapper sysRoleMenuMapper,
                               PasswordEncoder passwordEncoder) {
        this.adminQueryMapper = adminQueryMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleDataScopeDeptMapper = sysRoleDataScopeDeptMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
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
    public AdminRoleDto updateRoleDataScope(Long roleId, RoleDataScopeUpdateRequest request) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getDeleted() != null && role.getDeleted() != 0) {
            throw new BusinessException(404, "Role not found");
        }
        String scope = normalizeDataScope(request.dataScope());
        role.setDataScope(scope);
        sysRoleMapper.updateById(role);
        sysRoleDataScopeDeptMapper.deleteByRoleId(roleId);
        if ("custom".equals(scope)) {
            for (Long deptId : normalizeDeptIds(request.deptIds())) {
                sysRoleDataScopeDeptMapper.insertRoleDept(roleId, deptId);
            }
        }
        return toRoleDto(new AdminQueryMapper.RoleRow(role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus(), role.getDataScope()));
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

    public List<MenuDto> listAllMenus() {
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, "enabled")
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortNo)
                .orderByAsc(SysMenu::getId));
        return buildMenuTree(menus, 0L);
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
        return new AdminRoleDto(
                role.id(),
                role.roleCode(),
                role.roleName(),
                role.status(),
                role.dataScope(),
                sysRoleDataScopeDeptMapper.selectDeptIdsByRoleId(role.id())
        );
    }

    private AdminRoleDto toRoleDto(AdminQueryMapper.UserRoleRow role) {
        return new AdminRoleDto(role.id(), role.roleCode(), role.roleName(), role.status(), null, List.of());
    }

    private String normalizeDataScope(String dataScope) {
        String value = trimToNull(dataScope);
        if (value == null) {
            return "self";
        }
        String normalized = value.toLowerCase();
        if (List.of("all", "dept", "dept_sub", "self", "custom").contains(normalized)) {
            return normalized;
        }
        throw new BusinessException("Unsupported data scope");
    }

    private List<Long> normalizeDeptIds(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return deptIds.stream().filter(Objects::nonNull).distinct().toList();
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

    private List<Long> normalizeMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        return menuIds.stream()
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

    public List<Long> getRoleMenuIds(Long roleId) {
        return sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                .eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
    }

    @Transactional
    public void assignRoleMenus(Long roleId, List<Long> menuIds) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getDeleted() != null && role.getDeleted() != 0) {
            throw new BusinessException(404, "Role not found");
        }
        List<Long> normalizedMenuIds = normalizeMenuIds(menuIds);
        if (!normalizedMenuIds.isEmpty()) {
            Long matchedCount = sysMenuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                    .in(SysMenu::getId, normalizedMenuIds)
                    .eq(SysMenu::getStatus, "enabled")
                    .eq(SysMenu::getDeleted, 0));
            if (matchedCount != normalizedMenuIds.size()) {
                throw new BusinessException("One or more menus do not exist");
            }
        }
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery()
                .eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : normalizedMenuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenu.setCreatedTime(java.time.LocalDateTime.now());
            sysRoleMenuMapper.insert(roleMenu);
        }
    }

    private List<MenuDto> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .map(menu -> new MenuDto(
                        menu.getId(),
                        menu.getParentId(),
                        menu.getMenuName(),
                        menu.getMenuCode(),
                        menu.getPath(),
                        menu.getComponent(),
                        menu.getMenuType(),
                        menu.getIcon(),
                        menu.getSortNo(),
                        buildMenuTree(menus, menu.getId())
                ))
                .toList();
    }
}
