package com.example.judicialappraisal.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformCatalogServiceTests {

    private final PlatformCatalogService service = new PlatformCatalogService();

    @Test
    void judicialCatalogUsesManualScopeNineteenWorkflows() {
        var catalog = service.judicialCatalog();

        assertThat(catalog.workflowCount()).isEqualTo(19);
        assertThat(catalog.formCount()).isEqualTo(19);
        assertThat(catalog.workflows()).extracting("code")
                .contains("received-entrust", "expense-reimbursement", "seal-application")
                .doesNotContain("case-suspension");
    }

    @Test
    void menusOnlyExposeManualScopeGroups() {
        var menus = service.menus();

        assertThat(menus).extracting("code")
                .containsExactly("quick", "personal", "workflow", "knowledge");
        assertThat(menus.stream().flatMap(menu -> menu.children().stream())).extracting("title")
                .contains("新建工作", "我的工作", "工作查询", "数据报表", "知识库");
    }
}
