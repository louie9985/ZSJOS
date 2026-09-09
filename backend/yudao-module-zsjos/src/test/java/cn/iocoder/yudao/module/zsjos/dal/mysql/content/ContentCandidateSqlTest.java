package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentCandidateSqlTest {

    @Test
    void reviewCandidatesUseCurrentAccountOwnerAndProjectTheJoinedVersion() {
        String sql = ContentMapper.CandidateSqlProvider.reviewCandidateSql();

        assertTrue(sql.contains("a.owner_operator_user_id=#{userId}"));
        assertTrue(sql.contains("v.version_no=c.current_version_no"));
        assertTrue(sql.contains("v.id AS contentVersionId"));
        assertFalse(sql.contains("c.owner_operator_user_id=#{userId}"));
    }

    @Test
    void referenceTargetsApplyVisibilityAndVersionRulesInThePagedQuery() {
        String sql = ContentMapper.CandidateSqlProvider.referenceTargetSql();

        assertTrue(sql.contains("v.version_no=c.current_version_no"));
        assertTrue(sql.contains("v.frozen_at IS NULL"));
        assertTrue(sql.contains("v.id AS contentVersionId"));
        assertTrue(sql.contains("#{all}=TRUE"));
    }
}
