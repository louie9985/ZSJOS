package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminDataPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowDeleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowUpdateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDataRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDetailRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.DATABASE_ADMIN_EXECUTE_FAIL;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.DATABASE_ADMIN_SENSITIVE_COLUMN;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.DATABASE_ADMIN_TABLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.DATABASE_ADMIN_TABLE_READONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

public class DatabaseAdminServiceImplTest extends BaseMockitoUnitTest {

    private static final Long DATA_SOURCE_CONFIG_ID = 10L;
    private static final String URL = "jdbc:h2:mem:database_admin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @InjectMocks
    private DatabaseAdminServiceImpl databaseAdminService;

    @Mock
    private DataSourceConfigService dataSourceConfigService;

    @BeforeEach
    public void setUp() throws Exception {
        lenient().when(dataSourceConfigService.getDataSourceConfig(eq(DATA_SOURCE_CONFIG_ID)))
                .thenReturn(new DataSourceConfigDO().setId(DATA_SOURCE_CONFIG_ID).setName("test")
                        .setUrl(URL).setUsername("sa").setPassword(""));
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS db_admin_user");
            statement.execute("DROP TABLE IF EXISTS db_admin_no_pk");
            statement.execute("""
                    CREATE TABLE db_admin_user (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      username VARCHAR(64) NOT NULL,
                      password VARCHAR(64),
                      token_value VARCHAR(64),
                      age INT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE db_admin_no_pk (
                      name VARCHAR(64) NOT NULL,
                      remark VARCHAR(64)
                    )
                    """);
            statement.execute("INSERT INTO db_admin_user(username, password, token_value, age) VALUES ('admin', 'raw-password', 'raw-token', 18)");
            statement.execute("INSERT INTO db_admin_user(username, password, token_value, age) VALUES ('guest', 'guest-password', 'guest-token', 20)");
            statement.execute("INSERT INTO db_admin_no_pk(name, remark) VALUES ('readonly', 'no primary key')");
        }
    }

    @Test
    public void testGetTableList() {
        List<DatabaseAdminTableRespVO> tables = databaseAdminService.getTableList(DATA_SOURCE_CONFIG_ID, "user", null);

        assertEquals(1, tables.size());
        assertEquals("db_admin_user", tables.get(0).getName());
        assertEquals("id", tables.get(0).getPrimaryKeyColumn());
        assertTrue(tables.get(0).getWritable());
    }

    @Test
    public void testGetTableDetail_sensitiveColumnsReadonly() {
        DatabaseAdminTableDetailRespVO table = databaseAdminService.getTableDetail(DATA_SOURCE_CONFIG_ID, "db_admin_user");

        assertEquals("id", table.getPrimaryKeyColumn());
        assertTrue(table.getWritable());
        assertTrue(table.getColumns().stream().anyMatch(column -> column.getName().equals("password")
                && column.getSensitive() && !column.getEditable()));
        assertTrue(table.getColumns().stream().anyMatch(column -> column.getName().equals("token_value")
                && column.getSensitive() && !column.getEditable()));
        assertTrue(table.getColumns().stream().anyMatch(column -> column.getName().equals("username")
                && !column.getSensitive() && column.getEditable()));
    }

    @Test
    public void testGetTableDataPage_maskSensitiveValues() {
        DatabaseAdminDataPageReqVO reqVO = new DatabaseAdminDataPageReqVO();
        reqVO.setDataSourceConfigId(DATA_SOURCE_CONFIG_ID);
        reqVO.setTableName("db_admin_user");
        reqVO.setKeyword("adm");
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        DatabaseAdminTableDataRespVO page = databaseAdminService.getTableDataPage(reqVO);

        assertEquals(1L, page.getTotal());
        assertEquals("admin", page.getRows().get(0).get("username"));
        assertEquals("******", page.getRows().get(0).get("password"));
        assertEquals("******", page.getRows().get(0).get("token_value"));
    }

    @Test
    public void testCreateUpdateDeleteRow_success() throws Exception {
        databaseAdminService.createRow(new DatabaseAdminRowCreateReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_user")
                .setValues(Map.of("username", "new-user", "age", 30)));
        Long id = selectLong("SELECT id FROM db_admin_user WHERE username = 'new-user'");

        databaseAdminService.updateRow(new DatabaseAdminRowUpdateReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_user")
                .setPrimaryKeyValue(id)
                .setValues(Map.of("username", "updated-user")));
        assertEquals("updated-user", selectString("SELECT username FROM db_admin_user WHERE id = " + id));

        databaseAdminService.deleteRow(new DatabaseAdminRowDeleteReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_user")
                .setPrimaryKeyValue(id));
        assertNull(selectString("SELECT username FROM db_admin_user WHERE id = " + id));
    }

    @Test
    public void testUpdateRow_noPrimaryKeyReadonly() {
        assertServiceException(() -> databaseAdminService.updateRow(new DatabaseAdminRowUpdateReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_no_pk")
                .setPrimaryKeyValue("readonly")
                .setValues(Map.of("remark", "changed"))), DATABASE_ADMIN_TABLE_READONLY);
    }

    @Test
    public void testUpdateRow_sensitiveColumnRejected() {
        ServiceException error = assertThrows(ServiceException.class, () -> databaseAdminService.updateRow(new DatabaseAdminRowUpdateReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_user")
                .setPrimaryKeyValue(1L)
                .setValues(Map.of("password", "changed"))));
        assertEquals(DATABASE_ADMIN_SENSITIVE_COLUMN.getCode(), error.getCode());
    }

    @Test
    public void testCreateRow_executeFail() {
        assertServiceException(() -> databaseAdminService.createRow(new DatabaseAdminRowCreateReqVO()
                .setDataSourceConfigId(DATA_SOURCE_CONFIG_ID)
                .setTableName("db_admin_user")
                .setValues(Map.of("username", "bad-age", "age", "not-a-number"))), DATABASE_ADMIN_EXECUTE_FAIL);
    }

    @Test
    public void testGetTableDetail_unknownTableRejected() {
        assertServiceException(() -> databaseAdminService.getTableDetail(DATA_SOURCE_CONFIG_ID,
                "db_admin_user where 1 = 1"), DATABASE_ADMIN_TABLE_NOT_EXISTS);
    }

    @Test
    public void testGetTableDetail_noPrimaryKeyReadonly() {
        DatabaseAdminTableDetailRespVO table = databaseAdminService.getTableDetail(DATA_SOURCE_CONFIG_ID, "db_admin_no_pk");

        assertFalse(table.getWritable());
        assertNull(table.getPrimaryKeyColumn());
    }

    @Test
    public void testNormalizeRemark_mojibakeRecovered() {
        String mojibake = new String("客资业务编号".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        assertEquals("客资业务编号", DatabaseAdminServiceImpl.normalizeRemark(mojibake));
        assertEquals("客资业务编号", DatabaseAdminServiceImpl.normalizeRemark("客资业务编号"));
    }

    private Long selectLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private String selectString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

}
