package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PersonMapperSqlTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void servicePeriodFiltersAllMediaReadPathsBeforePagination() {
        for (Boolean flag : new Boolean[]{true, false, null}) {
            PersonMapper mapper = mock(PersonMapper.class, Answers.CALLS_REAL_METHODS);
            doAnswer(invocation -> {
                var wrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) invocation.getArgument(1);
                String sql = wrapper.getSqlSegment();
                assertEquals(flag != null, sql.contains("in_service_period"));
                if (flag != null) assertTrue(wrapper.getParamNameValuePairs().containsValue(flag));
                assertTrue(sql.contains("tenant_id=zsjos_person.tenant_id"));
                assertTrue(sql.contains("deleted=b'0'"));
                assertTrue(sql.contains("name LIKE"));
                return invocation.getArgument(0);
            }).when(mapper).selectPage(any(IPage.class), any(Wrapper.class));
            var request = new MyStudentPageReqVO();
            request.setInServicePeriod(flag); request.setKeyword("测试");
            mapper.selectMediaStudentPage(request, 10L);
            mapper.selectAllMediaStudentPage(request, null);
            mapper.selectTenantReadStudentPage(request, null);
        }
    }

    @Test
    void duplicateCandidateSqlIncludesVerifiedAliasesAndRemainsParserCompatible() throws Exception {
        assertDuplicateCandidateSql(Map.of("mobile", "13800000000"), 6);
        assertDuplicateCandidateSql(Map.of("wechatId", "CaseSensitiveWechat"), 6);
        assertDuplicateCandidateSql(Map.of("mobile", "13800000000", "wechatId", "CaseSensitiveWechat"), 8);
    }

    @Test
    void selectMyStudentPageBuildsSqlWithoutUnusedStatusArgument() {
        PersonMapper mapper = mapperThatBuildsSqlSegment();
        MyStudentPageReqVO request = new MyStudentPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);

        assertDoesNotThrow(() -> mapper.selectMyStudentPage(request, 241L, null));
    }

    @Test
    void selectMyStudentPageBuildsSqlWithStatusArgument() {
        PersonMapper mapper = mapperThatBuildsSqlSegment();
        MyStudentPageReqVO request = new MyStudentPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setServiceStatus("paused");

        assertDoesNotThrow(() -> mapper.selectMyStudentPage(request, 241L, null));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private PersonMapper mapperThatBuildsSqlSegment() {
        PersonMapper mapper = mock(PersonMapper.class, Answers.CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(1);
            wrapper.getSqlSegment();
            return invocation.getArgument(0);
        }).when(mapper).selectPage(any(IPage.class), any(Wrapper.class));
        return mapper;
    }

    private static void assertDuplicateCandidateSql(Map<String, String> parameters,
                                                    int parameterCount) throws Exception {
        Method method = PersonMapper.class.getMethod("selectDuplicateCandidates", String.class, String.class);
        String script = String.join("", method.getAnnotation(Select.class).value());
        Configuration configuration = new Configuration();
        SqlSource source = new XMLLanguageDriver().createSqlSource(configuration, script, Map.class);
        BoundSql boundSql = source.getBoundSql(new HashMap<>(parameters));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("CAST(mobile AS BINARY)=CAST(? AS BINARY)"));
        assertTrue(sql.contains("LOWER(wechat_id)=LOWER(?)"));
        assertTrue(sql.contains("ca.tenant_id=zsjos_person.tenant_id"));
        assertTrue(sql.contains("ca.person_id=zsjos_person.id"));
        assertTrue(sql.contains("ca.deleted=b'0'"));
        assertTrue(sql.contains("LOWER(ca.contact_value)=LOWER(?)"));
        assertFalse(sql.contains("BINARY mobile"));
        assertEquals(parameterCount, boundSql.getParameterMappings().size());
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));

        Method lockedMethod = PersonMapper.class.getMethod("selectDuplicateCandidatesForUpdate",
                String.class, String.class, Long.class);
        String lockedScript = String.join("", lockedMethod.getAnnotation(Select.class).value());
        Map<String, Object> lockedParameters = new HashMap<>(parameters);
        lockedParameters.put("tenantId", 1L);
        BoundSql locked = new XMLLanguageDriver().createSqlSource(configuration, lockedScript, Map.class)
                .getBoundSql(lockedParameters);
        assertEquals(parameterCount + 1, locked.getParameterMappings().size());
        assertTrue(locked.getSql().contains("tenant_id=?"));
        assertTrue(locked.getSql().contains("FOR UPDATE"));
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(locked.getSql()));
    }

    /**
     * WeChat 去重是不区分大小写的（LOWER 相等即视为同一联系人），手机号仍严格区分大小写（BINARY）。
     */
    @Test
    void wechatMatchIsCaseInsensitiveWhileMobileRemainsBinary() throws Exception {
        assertDuplicateCandidateSql(Map.of("wechatId", "CaseSensitiveWechat"), 6);
    }
}
