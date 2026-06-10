package com.example.judicialappraisal.config;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.common.annotation.DataScope;
import java.lang.reflect.Method;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;

@Component
public class CustomDataPermissionHandler implements DataPermissionHandler {

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return where;
        }

        // Determine max data scope
        // all (1) > dept_sub (2) > dept (3) > self (4)
        // Here we just mock a simple self scope for demonstration. 
        // In a full implementation, we would check userInfo.roles() dataScope fields.
        boolean isAll = userInfo.roles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.code()));
        if (isAll) {
            return where;
        }

        // Mocking self scope
        EqualsTo selfScope = new EqualsTo();
        selfScope.setLeftExpression(new Column("created_by"));
        selfScope.setRightExpression(new LongValue(userInfo.id()));

        if (where == null) {
            return selfScope;
        }
        return new AndExpression(where, selfScope);
    }
}
