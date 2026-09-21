package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the student page predicates against the MyBatis Plus placeholder trap.
 *
 * <p>{@code AbstractWrapper.formatSqlMaybeWithParam} rewrites "{n}" placeholders by position and throws
 * {@code MybatisPlusException: Please check the syntax correctness! sql not contains: "{n}"} as soon as an
 * argument has no matching placeholder. Building the status condition by branching the arguments instead
 * of the SQL therefore broke every status-less read — the delivery supervisor's default view — with a 500.
 *
 * <p>These assertions run the real production predicate builders and resolve them exactly the way MyBatis
 * Plus does, so a mismatch fails here instead of in production.
 */
class PersonMapperStudentScopeSqlTest {

    private static final String MANAGED_PREFIX = "EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE "
            + "AND ({0} IS NULL OR sr.class_id={0}) AND sr.owner_user_id IN (15,27) AND ";

    private static final String ALL_PREFIX = "EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE "
            + "AND ({0} IS NULL OR sr.class_id={0}) AND ";

    /** Resolves the fragment the way MyBatis Plus does, surfacing placeholder/argument mismatches. */
    private static String resolve(String prefix, Long classId, String serviceStatus) {
        PersonMapper.StudentScopePredicate predicate =
                PersonMapper.buildStudentScopePredicate(prefix, classId, serviceStatus);
        QueryWrapperX<Object> query = new QueryWrapperX<>();
        query.apply(predicate.sql(), predicate.args());
        String sql = query.getSqlSegment();   // raises MybatisPlusException on an unconsumed argument
        assertUnresolved(sql);
        return sql;
    }

    /**
     * A placeholder MyBatis Plus did not rewrite survives literally as "{n}"; the ones it did are wrapped
     * in "#{ew.paramNameValuePairs.MPGENVALn}", so strip the resolved ones before looking for leftovers.
     */
    private static void assertUnresolved(String sql) {
        String withoutBindings = sql.replaceAll("#\\{[^}]*}", "");
        assertFalse(withoutBindings.contains("{"), sql + " <- unresolved placeholder left in the fragment");
    }

    @Test
    void managedPageWithoutServiceStatusBuilds() {
        String sql = resolve(MANAGED_PREFIX, null, null);
        assertTrue(sql.contains("sr.status IN ('active','paused','completed')"), sql);
    }

    @Test
    void managedPageWithServiceStatusBuilds() {
        String sql = resolve(MANAGED_PREFIX, null, "paused");
        assertTrue(sql.contains("sr.status=#{ew.paramNameValuePairs."), sql);
    }

    @Test
    void managedPageWithoutServiceStatusBindsClassIdOnly() {
        String sql = resolve(MANAGED_PREFIX, 42L, null);
        assertTrue(sql.contains("sr.class_id=#{ew.paramNameValuePairs."), sql);
        assertFalse(sql.contains("sr.status=#{"), sql + " <- a null status must not bind a placeholder");
    }

    @Test
    void allDepartmentPageWithoutServiceStatusBuilds() {
        String sql = resolve(ALL_PREFIX, null, null);
        assertTrue(sql.contains("sr.status IN ('active','paused','completed')"), sql);
    }

    @Test
    void allDepartmentPageWithServiceStatusBuilds() {
        String sql = resolve(ALL_PREFIX, null, "completed");
        assertTrue(sql.contains("sr.status=#{ew.paramNameValuePairs."), sql);
    }
}
