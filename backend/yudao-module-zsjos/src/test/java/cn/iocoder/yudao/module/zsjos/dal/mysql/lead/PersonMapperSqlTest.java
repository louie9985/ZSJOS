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
    void duplicateCandidateSqlRemainsCaseSensitiveAndTenantParserCompatible() throws Exception {
        assertDuplicateCandidateSql(Map.of("mobile", "13800000000"), 2);
        assertDuplicateCandidateSql(Map.of("wechatId", "CaseSensitiveWechat"), 2);
        assertDuplicateCandidateSql(Map.of("mobile", "13800000000", "wechatId", "CaseSensitiveWechat"), 4);
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
        assertTrue(sql.contains("CAST(wechat_id AS BINARY)=CAST(? AS BINARY)"));
        assertFalse(sql.contains("BINARY mobile"));
        assertEquals(parameterCount, boundSql.getParameterMappings().size());
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
    }
}
