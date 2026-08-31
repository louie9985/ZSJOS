package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
