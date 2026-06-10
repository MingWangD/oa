package com.example.judicialappraisal.organization.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.entity.SysMenu;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final SysMenuMapper sysMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public PermissionService(SysMenuMapper sysMenuMapper,
                             SysUserRoleMapper sysUserRoleMapper,
                             SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    public List<MenuDto> getMenusByUserId(Long userId) {
        List<SysMenu> allMenus = getMenusForUser(userId);
        return buildMenuTree(allMenus, 0L);
    }

    public Set<String> getPermissionsByUserId(Long userId) {
        return getMenusForUser(userId).stream()
                .map(SysMenu::getMenuCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<SysMenu> getMenusForUser(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        
        // Check if user is ADMIN
        boolean isAdmin = roleIds.contains(1L); // Assuming ID 1 is ADMIN for now
        if (isAdmin) {
            return sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getStatus, "enabled")
                    .eq(SysMenu::getDeleted, 0)
                    .orderByAsc(SysMenu::getSortNo));
        }

        List<Long> menuIds = sysRoleMenuMapper.selectList(Wrappers.<com.example.judicialappraisal.organization.entity.SysRoleMenu>lambdaQuery()
                        .in(com.example.judicialappraisal.organization.entity.SysRoleMenu::getRoleId, roleIds))
                .stream()
                .map(com.example.judicialappraisal.organization.entity.SysRoleMenu::getMenuId)
                .distinct()
                .toList();

        if (menuIds.isEmpty()) {
            return List.of();
        }

        return sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, "enabled")
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortNo));
    }

    private List<MenuDto> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(m -> new MenuDto(
                        m.getId(),
                        m.getParentId(),
                        m.getMenuName(),
                        m.getMenuCode(),
                        m.getPath(),
                        m.getComponent(),
                        m.getMenuType(),
                        m.getIcon(),
                        m.getSortNo(),
                        buildMenuTree(menus, m.getId())
                ))
                .collect(Collectors.toList());
    }
}
