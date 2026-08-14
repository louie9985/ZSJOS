package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonMapperSqlTest {

    @Test
    void duplicateCandidateSqlRemainsCaseSensitiveAndTenantParserCompatible() throws Exception {
        assertSql(Map.of("mobile", "13800000000"), 2);
        assertSql(Map.of("wechatId", "CaseSensitiveWechat"), 2);
        assertSql(Map.of("mobile", "13800000000", "wechatId", "CaseSensitiveWechat"), 4);
    }

    private static void assertSql(Map<String, String> parameters, int parameterCount) throws Exception {
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
