package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.*;
import cn.iocoder.yudao.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Opt-in contract test. Only creates uniquely named synthetic tables in a dedicated test database. */
@EnabledIfEnvironmentVariable(named = "DATABASE_ADMIN_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/codex_db_admin_[a-zA-Z0-9_]+\\?.*")
class DatabaseAdminMySqlTest extends BaseMockitoUnitTest {
    @Mock
    private DataSourceConfigService dataSourceConfigService;
    @InjectMocks
    private DatabaseAdminServiceImpl service;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mysqlRoundTripAndFailureContract(boolean changedRows) throws Exception {
        String baseUrl = System.getenv("DATABASE_ADMIN_MYSQL_URL");
        assertFalse(baseUrl.contains("useAffectedRows="), "The test sets both affected-row modes itself");
        String url = baseUrl + "&useAffectedRows=" + changedRows;
        String username = System.getenv("DATABASE_ADMIN_MYSQL_USER");
        String password = System.getenv("DATABASE_ADMIN_MYSQL_PASSWORD");
        when(dataSourceConfigService.getDataSourceConfig(10L)).thenReturn(new DataSourceConfigDO()
                .setUrl(url).setUsername(username).setPassword(password));
        String table = "editor_" + Long.toUnsignedString(System.nanoTime());
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("SET NAMES utf8mb4");
            statement.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                    + " flag BIT(1) NOT NULL DEFAULT 0, tiny TINYINT(1) NOT NULL DEFAULT 2,"
                    + " bits BIT(8), payload LONGTEXT, note VARCHAR(64) DEFAULT 'default-note',"
                    + " amount DECIMAL(30,6), at_time DATETIME(6), duration TIME(6), doc JSON,"
                    + " unique_value INT UNIQUE, parent_id BIGINT,"
                    + " derived INT GENERATED ALWAYS AS (tiny + 1) STORED,"
                    + " updated DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),"
                    + " FOREIGN KEY(parent_id) REFERENCES " + table + "(id)) CHARACTER SET utf8mb4");
            statement.execute("INSERT INTO " + table + " (id) VALUES (9007199254740993)");
            try (var ps = connection.prepareStatement("UPDATE " + table + " SET flag=? WHERE id=9007199254740993")) {
                ps.setObject(1, "false");
                SQLException original = assertThrows(SQLException.class, ps::executeUpdate);
                assertEquals(1406, original.getErrorCode());
                assertEquals("22001", original.getSQLState());
            }

            var detail = service.getTableDetail(10L, table);
            assertEquals("integer", detail.getColumns().stream().filter(c -> c.getName().equals("tiny")).findFirst().orElseThrow().getValueKind());
            assertFalse(detail.getColumns().stream().filter(c -> c.getName().equals("bits")).findFirst().orElseThrow().getEditable());
            assertFalse(detail.getColumns().stream().filter(c -> c.getName().equals("derived")).findFirst().orElseThrow().getEditable());
            String payload = "{\"title\":\"测试\",\"url\":\"https://example.invalid/new?x=1&y=2\"}";
            update(table, Map.of("payload", payload, "amount", "123456789012345678.123456",
                    "at_time", "2026-09-06 12:34:56.123456", "duration", "-120:00:00.123456", "doc", "{\"n\":9007199254740993}"));
            var pageReq = new DatabaseAdminDataPageReqVO().setDataSourceConfigId(10L).setTableName(table);
            pageReq.setPageNo(1);
            pageReq.setPageSize(10);
            Map<String, Object> before = service.getTableDataPage(pageReq).getRows().getFirst();
            assertEquals("9007199254740993", before.get("id"));
            assertEquals(false, before.get("flag"));
            assertEquals("2", before.get("tiny"));
            assertEquals("123456789012345678.123456", before.get("amount"));
            assertEquals("2026-09-06 12:34:56.123456", before.get("at_time"));
            assertEquals("-120:00:00.123456", before.get("duration"));
            String changedPayload = payload.replace("/new?", "/changed?");
            update(table, Map.of("payload", changedPayload));
            var after = service.getTableDataPage(pageReq).getRows().getFirst();
            for (String key : before.keySet()) {
                if (!key.equals("payload") && !key.equals("updated")) assertEquals(before.get(key), after.get(key), key);
            }
            assertEquals(changedPayload, after.get("payload"));
            try (var rs = statement.executeQuery("SELECT HEX(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.title')) USING utf8mb4)) FROM " + table + " WHERE id=9007199254740993")) {
                assertTrue(rs.next());
                assertEquals("E6B58BE8AF95", rs.getString(1));
            }
            update(table, Map.of("flag", "true", "note", ""));
            assertEquals(true, service.getTableDataPage(pageReq).getRows().getFirst().get("flag"));
            assertEquals("", service.getTableDataPage(pageReq).getRows().getFirst().get("note"));
            Map<String, Object> nullValue = new LinkedHashMap<>();
            nullValue.put("note", null);
            update(table, nullValue);
            assertNull(service.getTableDataPage(pageReq).getRows().getFirst().get("note"));
            update(table, nullValue); // useAffectedRows=true: unchanged existing rows are successful.
            nullValue.clear();
            nullValue.put("flag", null);
            assertCode(DATABASE_ADMIN_NOT_NULL.getCode(), () -> update(table, nullValue));
            assertCode(DATABASE_ADMIN_VALUE_INVALID.getCode(), () -> update(table, Map.of("doc", "{")));
            assertCode(DATABASE_ADMIN_DATA_LIMIT.getCode(), () -> update(table, Map.of("amount", "0.1234567")));
            assertCode(DATABASE_ADMIN_COLUMN_READONLY.getCode(), () -> update(table, Map.of("derived", "5")));
            assertCode(DATABASE_ADMIN_REFERENCE.getCode(), () -> update(table, Map.of("parent_id", "123")));
            service.createRow(new DatabaseAdminRowCreateReqVO().setDataSourceConfigId(10L).setTableName(table).setValues(Map.of()));
            statement.execute("UPDATE " + table + " SET unique_value=1 WHERE id<>9007199254740993");
            assertCode(DATABASE_ADMIN_DUPLICATE.getCode(), () -> update(table, Map.of("unique_value", "1")));
            assertCode(DATABASE_ADMIN_ROW_NOT_EXISTS.getCode(), () -> service.updateRow(new DatabaseAdminRowUpdateReqVO()
                    .setDataSourceConfigId(10L).setTableName(table).setPrimaryKeyValue("42").setValues(Map.of("note", "missing"))));
            System.out.println("MySQL database editor contract passed; synthetic table retained: " + table);
        }
    }

    private void update(String table, Map<String, Object> values) {
        service.updateRow(new DatabaseAdminRowUpdateReqVO().setDataSourceConfigId(10L).setTableName(table)
                .setPrimaryKeyValue("9007199254740993").setValues(values));
    }

    private void assertCode(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(ServiceException.class, operation).getCode());
    }
}
