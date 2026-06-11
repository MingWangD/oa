package com.example.judicialappraisal.organization.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.entity.SysMenu;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public PermissionService(SysMenuMapper sysMenuMapper,
                             SysRoleMapper sysRoleMapper,
                             SysUserRoleMapper sysUserRoleMapper,
                             SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    public List<MenuDto> getMenusByUserId(Long userId) {
        List<SysMenu> allMenus = getMenusForUser(userId);
        return buildMenuTree(
                allMenus.stream().filter(menu -> !"F".equalsIgnoreCase(menu.getMenuType())).toList(),
                0L
        );
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

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
        List<SysRole> roles = sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, "enabled")
                .eq(SysRole::getDeleted, 0));
        boolean isAdmin = roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleCode()));
        if (isAdmin) {
            return getAllEnabledMenus();
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

        List<SysMenu> enabledMenus = getAllEnabledMenus();
        Map<Long, SysMenu> menuById = enabledMenus.stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu));
        Set<Long> visibleIds = new HashSet<>(menuIds);
        for (Long menuId : menuIds) {
            addAncestors(menuById, visibleIds, menuId);
        }
        return enabledMenus.stream().filter(menu -> visibleIds.contains(menu.getId())).toList();
    }

    private List<SysMenu> getAllEnabledMenus() {
        return sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, "enabled")
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortNo)
                .orderByAsc(SysMenu::getId));
    }

    private void addAncestors(Map<Long, SysMenu> menuById, Set<Long> visibleIds, Long menuId) {
        SysMenu current = menuById.get(menuId);
        Set<Long> visited = new HashSet<>();
        while (current != null
                && current.getParentId() != null
                && current.getParentId() != 0L
                && visited.add(current.getId())) {
            Long parentId = current.getParentId();
            visibleIds.add(parentId);
            current = menuById.get(parentId);
        }
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
