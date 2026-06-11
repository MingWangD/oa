package com.example.judicialappraisal.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.organization.dto.MenuDto;
import com.example.judicialappraisal.organization.entity.SysMenu;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysRoleMenu;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMenuMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionServiceTests {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
    private final PermissionService service = new PermissionService(menuMapper, roleMapper, userRoleMapper, roleMenuMapper);

    @Test
    void identifiesAdministratorByRoleCodeInsteadOfFixedId() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(91L, 88L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(88L, "ADMIN")));
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(1L, 0L, "系统管理", "M")));

        List<MenuDto> menus = service.getMenusByUserId(91L);

        assertThat(menus).extracting(MenuDto::menuName).containsExactly("系统管理");
    }

    @Test
    void includesAncestorDirectoriesAndExcludesButtonNodesFromMenuTree() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(91L, 5L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(5L, "REVIEWER")));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(5L, 3L), roleMenu(5L, 4L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(1L, 0L, "流程中心", "M"),
                menu(2L, 1L, "流程配置", "M"),
                menu(3L, 2L, "设计流程", "C"),
                menu(4L, 3L, "发布流程", "F")
        ));

        List<MenuDto> menus = service.getMenusByUserId(91L);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).children()).hasSize(1);
        assertThat(menus.get(0).children().get(0).children())
                .extracting(MenuDto::menuName)
                .containsExactly("设计流程");
        assertThat(service.getPermissionsByUserId(91L))
                .contains("permission-4");
    }

    private SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole value = new SysUserRole();
        value.setUserId(userId);
        value.setRoleId(roleId);
        return value;
    }

    private SysRole role(Long id, String code) {
        SysRole value = new SysRole();
        value.setId(id);
        value.setRoleCode(code);
        value.setStatus("enabled");
        value.setDeleted(0);
        return value;
    }

    private SysRoleMenu roleMenu(Long roleId, Long menuId) {
        SysRoleMenu value = new SysRoleMenu();
        value.setRoleId(roleId);
        value.setMenuId(menuId);
        return value;
    }

    private SysMenu menu(Long id, Long parentId, String name, String type) {
        SysMenu value = new SysMenu();
        value.setId(id);
        value.setParentId(parentId);
        value.setMenuName(name);
        value.setMenuCode("permission-" + id);
        value.setPath(type.equals("C") ? "/menu-" + id : null);
        value.setMenuType(type);
        value.setStatus("enabled");
        value.setDeleted(0);
        value.setSortNo(id.intValue());
        return value;
    }
}
