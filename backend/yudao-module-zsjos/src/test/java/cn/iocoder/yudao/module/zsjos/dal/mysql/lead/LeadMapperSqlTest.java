package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LeadMapperSqlTest {

    @Test
    void leadKeywordSearchesBusinessNumberAndContactsWithoutInternalId() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
        Method method = LeadMapper.class.getDeclaredMethod("applyLeadKeyword", LambdaQueryWrapperX.class, String.class);
        method.setAccessible(true);
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<>();

        method.invoke(null, query, "KZ20260831");

        String sql = query.getSqlSegment();
        assertTrue(sql.contains("lead_no"));
        assertTrue(sql.contains("submitted_name"));
        assertTrue(sql.contains("submitted_mobile"));
        assertTrue(sql.contains("submitted_wechat_id"));
        assertFalse(sql.matches("(?s).*\\bid\\s*=.*"));
    }

    @Test
    void countPartnerUnreachableSqlRemainsTenantParserCompatible() throws Exception {
        Method method = LeadMapper.class.getMethod("countPartnerUnreachable", Long.class);
        String sql = String.join("", method.getAnnotation(Select.class).value())
                .replaceAll("#\\{[^}]+}", "?");

        assertTrue(sql.contains("current_lead.result_value='unreachable'"));
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
    }

    @Test
    void latestFollowUpResultSqlRemainsTenantParserCompatible() throws Exception {
        Method method = LeadMapper.class.getDeclaredMethod("latestFollowUpResultSql", String.class);
        method.setAccessible(true);
        String condition = (String) method.invoke(null, "unreachable");
        String sql = "SELECT COUNT(*) FROM zsjos_lead WHERE partner_id=? AND deleted=b'0' AND " + condition;

        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
    }

    @Test
    void managementOrderUsesAllowlistedColumnAndStableIdTieBreak() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
        Method method = LeadMapper.class.getDeclaredMethod("applyManagementOrder",
                LambdaQueryWrapperX.class, LeadManagementPageReqVO.class);
        method.setAccessible(true);
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setSortField("leadNo");
        reqVO.setSortOrder("ascend");
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<>();

        method.invoke(null, query, reqVO);

        String sql = query.getSqlSegment().replaceAll("\\s+", " ");
        assertTrue(sql.contains("ORDER BY lead_no ASC,id DESC"), sql);
    }

    @Test
    void managementOrderDefaultsToActivityAndKeepsCursorOrder() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
        Method method = LeadMapper.class.getDeclaredMethod("applyManagementOrder",
                LambdaQueryWrapperX.class, LeadManagementPageReqVO.class);
        method.setAccessible(true);
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setSortField("submittedName");
        reqVO.setSortOrder("ascend");
        reqVO.setCursor("cursor-token");
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<>();

        method.invoke(null, query, reqVO);

        String sql = query.getSqlSegment().replaceAll("\\s+", " ");
        assertTrue(sql.contains("ORDER BY last_activity_at DESC,id DESC"), sql);
        assertFalse(sql.contains("submitted_name ASC"), sql);
    }

    @Test
    void managementSortValidationRejectsUnknownFieldAndDirection() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            LeadManagementPageReqVO valid = new LeadManagementPageReqVO();
            valid.setSortField("lastActivityAt");
            valid.setSortOrder("descend");
            assertEquals(0, validator.validate(valid).size());

            LeadManagementPageReqVO invalid = new LeadManagementPageReqVO();
            invalid.setSortField("ownerUserName");
            invalid.setSortOrder("DESC; DROP TABLE zsjos_lead");
            assertEquals(2, validator.validate(invalid).size());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void touchActivityIsTenantScopedAndOnlyMovesTimeForward() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
        LeadMapper mapper = mock(LeadMapper.class, CALLS_REAL_METHODS);
        doReturn(1).when(mapper).update(any(LeadDO.class), any());
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 6, 18, 30);
        TenantContextHolder.setTenantId(7L);
        try {
            mapper.touchActivity(42L, occurredAt);
        } finally {
            TenantContextHolder.clear();
        }

        org.mockito.ArgumentCaptor<LeadDO> entity = org.mockito.ArgumentCaptor.forClass(LeadDO.class);
        org.mockito.ArgumentCaptor<LambdaQueryWrapperX> wrapper =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).update(entity.capture(), wrapper.capture());
        assertEquals(occurredAt, entity.getValue().getLastActivityAt());
        String sql = wrapper.getValue().getSqlSegment().replaceAll("\\s+", " ");
        assertTrue(sql.contains("id ="), sql);
        assertTrue(sql.contains("tenant_id ="), sql);
        assertTrue(sql.contains("deleted ="), sql);
        assertTrue(sql.contains("last_activity_at IS NULL OR last_activity_at <"), sql);
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(42L));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(false));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(occurredAt));
    }

    @Test
    void advanceActivityDoesNotReplaceANewerValue() {
        LocalDateTime current = LocalDateTime.of(2026, 9, 6, 19, 0);
        LeadDO lead = new LeadDO().setLastActivityAt(current);

        LeadMapper.advanceActivity(lead, current.minusMinutes(1));
        assertEquals(current, lead.getLastActivityAt());

        LocalDateTime later = current.plusMinutes(1);
        LeadMapper.advanceActivity(lead, later);
        assertEquals(later, lead.getLastActivityAt());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void versionedActivityUpdateUsesMonotonicSql() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
        LeadMapper mapper = mock(LeadMapper.class, CALLS_REAL_METHODS);
        doReturn(1).when(mapper).update(any(), any());
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 6, 19, 30);

        assertEquals(1, mapper.updateVersionAndTouchActivity(42L, 3, occurredAt));

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper> wrapper =
                org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSet().replaceAll("\\s+", " ");
        assertTrue(sql.contains("last_activity_at = CASE WHEN last_activity_at IS NULL "
                + "OR last_activity_at <"), sql);
        assertTrue(sql.contains("ELSE last_activity_at END"), sql);
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(occurredAt));
    }
}
