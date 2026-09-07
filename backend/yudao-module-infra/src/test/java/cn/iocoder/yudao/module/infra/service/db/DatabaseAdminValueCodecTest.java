package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminColumnRespVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseAdminValueCodecTest {
    private DatabaseAdminColumnRespVO column(int jdbc, String type, long size, int scale) {
        DatabaseAdminColumnRespVO column = new DatabaseAdminColumnRespVO().setName("field")
                .setTypeName(type).setJdbcType(jdbc).setColumnSize(size).setDecimalDigits(scale).setNullable(true);
        return column.setValueKind(DatabaseAdminValueCodec.kind(column));
    }

    @Test
    void booleansAndNumericTinyintsAreDistinct() {
        var flag = column(Types.BIT, "BIT", 1, 0);
        assertEquals(false, DatabaseAdminValueCodec.convert(flag, "false"));
        assertEquals(true, DatabaseAdminValueCodec.convert(flag, "1"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(flag, "yes"));
        assertEquals("readonly", column(Types.BIT, "BIT", 8, 0).getValueKind());
        assertEquals(new BigDecimal("2"), DatabaseAdminValueCodec.convert(column(Types.TINYINT, "TINYINT", 3, 0), "2"));
    }

    @Test
    void exactNumbersAndRanges() {
        var unsigned = column(Types.BIGINT, "BIGINT UNSIGNED", 20, 0);
        assertEquals(new BigDecimal("18446744073709551615"), DatabaseAdminValueCodec.convert(unsigned, "18446744073709551615"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(unsigned, "18446744073709551616"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(unsigned, "-1"));
        var decimal = column(Types.DECIMAL, "DECIMAL", 30, 6);
        assertEquals(new BigDecimal("123456789012345678.123456"), DatabaseAdminValueCodec.convert(decimal, "123456789012345678.123456"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(decimal, "0.1234567"));
        assertEquals(BigDecimal.ZERO, DatabaseAdminValueCodec.convert(column(Types.DECIMAL, "DECIMAL", 2, 2), "0"));
    }

    @Test
    void textNullJsonAndTemporalPrecision() {
        var text = column(Types.LONGVARCHAR, "LONGTEXT", 1, 0);
        assertEquals("  {\n", DatabaseAdminValueCodec.convert(text, "  {\n"));
        assertEquals("", DatabaseAdminValueCodec.convert(text, ""));
        assertNull(DatabaseAdminValueCodec.convert(text, null));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(text.setNullable(false), null));
        var varchar = column(Types.VARCHAR, "VARCHAR", 1, 0);
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(varchar, "ab"));
        var json = column(Types.LONGVARCHAR, "JSON", 1000, 0);
        assertEquals("{\"n\":9007199254740993}", DatabaseAdminValueCodec.convert(json, "{\"n\":9007199254740993}"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(json, "{} {}"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(json, "{"));
        var date = column(Types.TIMESTAMP, "DATETIME", 26, 6);
        assertEquals("2026-09-06 12:34:56.123456", DatabaseAdminValueCodec.convert(date, "2026-09-06T12:34:56.123456"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(date, "2026-02-30 12:00:00"));
        assertThrows(ServiceException.class, () -> DatabaseAdminValueCodec.convert(date, "2026-09-06 12:34:56.1234567"));
        assertEquals("-120:00:00.123456", DatabaseAdminValueCodec.convert(column(Types.TIME, "TIME", 16, 6), "-120:00:00.123456"));
    }

    @Test
    void classifiesSqlErrorsWithoutExposingPayload() {
        int[][] errors = {{1062, DATABASE_ADMIN_DUPLICATE.getCode()}, {1048, DATABASE_ADMIN_NOT_NULL.getCode()},
                {1452, DATABASE_ADMIN_REFERENCE.getCode()}, {1406, DATABASE_ADMIN_DATA_LIMIT.getCode()},
                {1142, DATABASE_ADMIN_ACCESS_DENIED.getCode()}, {3819, DATABASE_ADMIN_CONSTRAINT.getCode()},
                {9999, DATABASE_ADMIN_EXECUTE_FAIL.getCode()}};
        for (int[] error : errors) {
            var result = DatabaseAdminServiceImpl.sqlFailure(new SQLException("secret-payload", "HY000", error[0]));
            assertEquals(error[1], result.getCode());
            assertFalse(result.getMessage().contains("secret-payload"));
        }
    }
}
