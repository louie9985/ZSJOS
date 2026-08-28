package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubordinateSalesCommandMapperSqlTest {

    @Test
    void completeSqlRemainsTenantParserCompatible() throws Exception {
        Method method = SubordinateSalesCommandMapper.class.getMethod(
                "complete", Long.class, Long.class, String.class, String.class);
        String sql = String.join("", method.getAnnotation(Update.class).value())
                .replaceAll("#\\{[^}]+}", "?");

        assertTrue(sql.contains("completed=b'0'"));
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
    }
}
