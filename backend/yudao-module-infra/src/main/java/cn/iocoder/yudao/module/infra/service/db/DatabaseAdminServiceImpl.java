package cn.iocoder.yudao.module.infra.service.db;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminColumnRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminDataPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowDeleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowUpdateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDataRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDetailRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_CREATE_SUB_TYPE;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_CREATE_SUCCESS;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_DELETE_SUB_TYPE;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_DELETE_SUCCESS;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_TYPE;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_UPDATE_SUB_TYPE;
import static cn.iocoder.yudao.module.infra.enums.LogRecordConstants.INFRA_DATABASE_ADMIN_UPDATE_SUCCESS;

@Service
@Validated
@Slf4j
public class DatabaseAdminServiceImpl implements DatabaseAdminService {

    private static final Set<String> SENSITIVE_COLUMN_TOKENS = Set.of(
            "password", "passwd", "pwd", "token", "secret", "private_key", "access_key",
            "refresh_token", "credential", "salt", "api_key", "apikey");

    @Resource
    private DataSourceConfigService dataSourceConfigService;

    @Override
    public List<DatabaseAdminTableRespVO> getTableList(Long dataSourceConfigId, String name, String comment) {
        return execute(dataSourceConfigId, "table-list", null, connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            List<DatabaseAdminTableRespVO> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), getSchemaPattern(connection), null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = normalizeRemark(rs.getString("REMARKS"));
                    if (StrUtil.isNotEmpty(name) && !StrUtil.containsIgnoreCase(tableName, name)) {
                        continue;
                    }
                    if (StrUtil.isNotEmpty(comment) && !StrUtil.containsIgnoreCase(remarks, comment)) {
                        continue;
                    }
                    tables.add(buildTable(metaData, connection, tableName, remarks));
                }
            }
            tables.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return tables;
        });
    }

    @Override
    public DatabaseAdminTableDetailRespVO getTableDetail(Long dataSourceConfigId, String tableName) {
        return execute(dataSourceConfigId, "table-detail", tableName, connection -> getTableDetail(connection, tableName));
    }

    @Override
    public DatabaseAdminTableDataRespVO getTableDataPage(DatabaseAdminDataPageReqVO reqVO) {
        return execute(reqVO.getDataSourceConfigId(), "data-page", reqVO.getTableName(), connection -> {
            DatabaseAdminTableDetailRespVO table = getTableDetail(connection, reqVO.getTableName());
            String tableIdentifier = quoteIdentifier(connection, table.getName());
            QueryClause where = buildKeywordWhere(connection, table.getColumns(), reqVO.getKeyword());
            String orderBy = StrUtil.isNotEmpty(table.getPrimaryKeyColumn())
                    ? " ORDER BY " + quoteIdentifier(connection, table.getPrimaryKeyColumn()) + " DESC" : "";
            long offset = (long) (reqVO.getPageNo() - 1) * reqVO.getPageSize();

            Long total;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + tableIdentifier + where.sql())) {
                bindParameters(ps, where.parameters());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            String sql = "SELECT * FROM " + tableIdentifier + where.sql() + orderBy + " LIMIT ? OFFSET ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = bindParameters(ps, where.parameters());
                ps.setInt(index++, reqVO.getPageSize());
                ps.setLong(index, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    while (rs.next()) {
                        rows.add(readRow(rs, resultSetMetaData, table.getColumns()));
                    }
                }
            }

            return new DatabaseAdminTableDataRespVO()
                    .setTable(table)
                    .setTotal(total)
                    .setRows(rows);
        });
    }

    @Override
    @LogRecord(type = INFRA_DATABASE_ADMIN_TYPE, subType = INFRA_DATABASE_ADMIN_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.dataSourceConfigId}}", success = INFRA_DATABASE_ADMIN_CREATE_SUCCESS)
    public void createRow(DatabaseAdminRowCreateReqVO reqVO) {
        execute(reqVO.getDataSourceConfigId(), "create", reqVO.getTableName(), connection -> {
            DatabaseAdminTableDetailRespVO table = getWritableTable(connection, reqVO.getTableName());
            Map<String, Object> values = validateValues(table, reqVO.getValues(), true);
            List<String> quotedColumns = new ArrayList<>();
            for (String column : values.keySet()) {
                quotedColumns.add(quoteIdentifier(connection, column));
            }
            String columns = String.join(", ", quotedColumns);
            String placeholders = values.keySet().stream().map(column -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO " + quoteIdentifier(connection, table.getName()) + " (" + columns + ") VALUES (" + placeholders + ")";
            if (values.isEmpty() && connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")) {
                sql = "INSERT INTO " + quoteIdentifier(connection, table.getName()) + " DEFAULT VALUES";
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bindValues(ps, table, values);
                int affected = ps.executeUpdate();
                if (affected != 1) {
                    throw exception(DATABASE_ADMIN_ROW_AFFECTED_INVALID);
                }
            }
            return null;
        });
    }

    @Override
    @LogRecord(type = INFRA_DATABASE_ADMIN_TYPE, subType = INFRA_DATABASE_ADMIN_UPDATE_SUB_TYPE,
            bizNo = "{{#reqVO.dataSourceConfigId}}", success = INFRA_DATABASE_ADMIN_UPDATE_SUCCESS)
    public void updateRow(DatabaseAdminRowUpdateReqVO reqVO) {
        execute(reqVO.getDataSourceConfigId(), "update", reqVO.getTableName(), connection -> {
            DatabaseAdminTableDetailRespVO table = getWritableTable(connection, reqVO.getTableName());
            Map<String, Object> values = validateValues(table, reqVO.getValues(), false);
            List<String> setExpressions = new ArrayList<>();
            for (String column : values.keySet()) {
                setExpressions.add(quoteIdentifier(connection, column) + " = ?");
            }
            String sets = String.join(", ", setExpressions);
            String sql = "UPDATE " + quoteIdentifier(connection, table.getName()) + " SET " + sets
                    + " WHERE " + quoteIdentifier(connection, table.getPrimaryKeyColumn()) + " = ?";
            DatabaseAdminColumnRespVO primaryKey = primaryKey(table);
            Object key = DatabaseAdminValueCodec.convert(primaryKey, reqVO.getPrimaryKeyValue());
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = bindValues(ps, table, values);
                DatabaseAdminValueCodec.bind(ps, index, primaryKey, key);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    assertRowExists(connection, table, primaryKey, key);
                } else if (affected != 1) {
                    throw exception(DATABASE_ADMIN_ROW_AFFECTED_INVALID);
                }
            }
            return null;
        });
    }

    @Override
    @LogRecord(type = INFRA_DATABASE_ADMIN_TYPE, subType = INFRA_DATABASE_ADMIN_DELETE_SUB_TYPE,
            bizNo = "{{#reqVO.dataSourceConfigId}}", success = INFRA_DATABASE_ADMIN_DELETE_SUCCESS)
    public void deleteRow(DatabaseAdminRowDeleteReqVO reqVO) {
        execute(reqVO.getDataSourceConfigId(), "delete", reqVO.getTableName(), connection -> {
            DatabaseAdminTableDetailRespVO table = getWritableTable(connection, reqVO.getTableName());
            String sql = "DELETE FROM " + quoteIdentifier(connection, table.getName())
                    + " WHERE " + quoteIdentifier(connection, table.getPrimaryKeyColumn()) + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                DatabaseAdminColumnRespVO primaryKey = primaryKey(table);
                DatabaseAdminValueCodec.bind(ps, 1, primaryKey,
                        DatabaseAdminValueCodec.convert(primaryKey, reqVO.getPrimaryKeyValue()));
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw exception(DATABASE_ADMIN_ROW_NOT_EXISTS);
                } else if (affected != 1) {
                    throw exception(DATABASE_ADMIN_ROW_AFFECTED_INVALID);
                }
            }
            return null;
        });
    }

    private DatabaseAdminTableDetailRespVO getWritableTable(Connection connection, String tableName) throws SQLException {
        DatabaseAdminTableDetailRespVO table = getTableDetail(connection, tableName);
        if (!Boolean.TRUE.equals(table.getWritable())) {
            throw exception(DATABASE_ADMIN_TABLE_READONLY);
        }
        return table;
    }

    private static DatabaseAdminColumnRespVO primaryKey(DatabaseAdminTableDetailRespVO table) {
        return table.getColumns().stream().filter(column -> column.getName().equals(table.getPrimaryKeyColumn()))
                .findFirst().orElseThrow(() -> exception(DATABASE_ADMIN_TABLE_READONLY));
    }

    private static int bindValues(PreparedStatement ps, DatabaseAdminTableDetailRespVO table,
                                  Map<String, Object> values) throws SQLException {
        Map<String, DatabaseAdminColumnRespVO> columns = table.getColumns().stream()
                .collect(Collectors.toMap(DatabaseAdminColumnRespVO::getName, column -> column));
        int index = 1;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            DatabaseAdminValueCodec.bind(ps, index++, columns.get(entry.getKey()), entry.getValue());
        }
        return index;
    }

    private static void assertRowExists(Connection connection, DatabaseAdminTableDetailRespVO table,
                                        DatabaseAdminColumnRespVO primaryKey, Object key) throws SQLException {
        String sql = "SELECT 1 FROM " + quoteIdentifier(connection, table.getName())
                + " WHERE " + quoteIdentifier(connection, primaryKey.getName()) + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            DatabaseAdminValueCodec.bind(ps, 1, primaryKey, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw exception(DATABASE_ADMIN_ROW_NOT_EXISTS);
                }
            }
        }
    }

    private DatabaseAdminTableDetailRespVO getTableDetail(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String actualTableName = findActualTableName(metaData, connection, tableName);
        String remarks = "";
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), getSchemaPattern(connection), literalPattern(metaData, actualTableName), new String[]{"TABLE"})) {
            if (rs.next()) {
                remarks = normalizeRemark(rs.getString("REMARKS"));
            }
        }
        DatabaseAdminTableRespVO table = buildTable(metaData, connection, actualTableName, remarks);
        List<DatabaseAdminColumnRespVO> columns = getColumns(metaData, connection, actualTableName);
        DatabaseAdminTableDetailRespVO detail = new DatabaseAdminTableDetailRespVO();
        detail.setName(table.getName());
        detail.setRemarks(table.getRemarks());
        detail.setPrimaryKeyColumn(table.getPrimaryKeyColumn());
        detail.setWritable(table.getWritable());
        detail.setColumns(columns);
        if (detail.getPrimaryKeyColumn() != null && "readonly".equals(primaryKey(detail).getValueKind())) {
            detail.setWritable(false);
        }
        return detail;
    }

    private DatabaseAdminTableRespVO buildTable(DatabaseMetaData metaData, Connection connection, String tableName,
                                               String remarks) throws SQLException {
        List<String> primaryKeys = getPrimaryKeys(metaData, connection, tableName);
        String primaryKeyColumn = primaryKeys.size() == 1 ? primaryKeys.get(0) : null;
        return new DatabaseAdminTableRespVO()
                .setName(tableName)
                .setRemarks(remarks)
                .setPrimaryKeyColumn(primaryKeyColumn)
                .setWritable(primaryKeyColumn != null);
    }

    private List<DatabaseAdminColumnRespVO> getColumns(DatabaseMetaData metaData, Connection connection,
                                                      String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>(getPrimaryKeys(metaData, connection, tableName));
        List<DatabaseAdminColumnRespVO> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), getSchemaPattern(connection), literalPattern(metaData, tableName), null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                boolean primaryKey = primaryKeys.contains(columnName);
                boolean autoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                boolean sensitive = isSensitiveColumn(columnName);
                columns.add(new DatabaseAdminColumnRespVO()
                        .setName(columnName)
                        .setTypeName(rs.getString("TYPE_NAME"))
                        .setJdbcType(rs.getInt("DATA_TYPE"))
                        .setColumnSize(rs.getLong("COLUMN_SIZE"))
                        .setDecimalDigits(rs.getObject("DECIMAL_DIGITS") == null ? null : rs.getInt("DECIMAL_DIGITS"))
                        .setDefaultValue(sensitive ? null : rs.getString("COLUMN_DEF"))
                        .setGenerated("YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN")))
                        .setRemarks(normalizeRemark(rs.getString("REMARKS")))
                        .setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                        .setPrimaryKey(primaryKey)
                        .setAutoIncrement(autoIncrement)
                        .setSensitive(sensitive)
                        .setEditable(!primaryKey && !autoIncrement && !sensitive));
            }
        }
        String product = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            // Connector/J may report TINYINT(1) as BIT. Native metadata preserves numeric semantics.
            try (PreparedStatement ps = connection.prepareStatement("SELECT COLUMN_NAME,COLUMN_TYPE,DATA_TYPE,"
                    + "CHARACTER_MAXIMUM_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE,DATETIME_PRECISION "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                ps.setString(1, connection.getCatalog());
                ps.setString(2, tableName);
                Map<String, DatabaseAdminColumnRespVO> byName = columns.stream()
                        .collect(Collectors.toMap(DatabaseAdminColumnRespVO::getName, column -> column));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DatabaseAdminColumnRespVO column = byName.get(rs.getString("COLUMN_NAME"));
                        if (column == null) {
                            continue;
                        }
                        String type = rs.getString("DATA_TYPE");
                        column.setTypeName(rs.getString("COLUMN_TYPE"));
                        if ("tinyint".equals(type)) {
                            column.setJdbcType(Types.TINYINT);
                        }
                        if (rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null) {
                            column.setColumnSize(rs.getLong("CHARACTER_MAXIMUM_LENGTH"));
                        } else if (rs.getObject("NUMERIC_PRECISION") != null) {
                            column.setColumnSize(rs.getLong("NUMERIC_PRECISION"));
                        }
                        if (rs.getObject("NUMERIC_SCALE") != null) {
                            column.setDecimalDigits(rs.getInt("NUMERIC_SCALE"));
                        } else if (rs.getObject("DATETIME_PRECISION") != null) {
                            column.setDecimalDigits(rs.getInt("DATETIME_PRECISION"));
                        }
                    }
                }
            }
        }
        columns.forEach(column -> {
            column.setValueKind(DatabaseAdminValueCodec.kind(column));
            column.setEditable(Boolean.TRUE.equals(column.getEditable())
                    && !Boolean.TRUE.equals(column.getGenerated()) && !"readonly".equals(column.getValueKind()));
        });
        return columns;
    }

    private List<String> getPrimaryKeys(DatabaseMetaData metaData, Connection connection, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(connection.getCatalog(), getSchemaPattern(connection), tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private String findActualTableName(DatabaseMetaData metaData, Connection connection, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), getSchemaPattern(connection), literalPattern(metaData, tableName), new String[]{"TABLE"})) {
            while (rs.next()) {
                if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return rs.getString("TABLE_NAME");
                }
            }
        }
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), getSchemaPattern(connection), null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String actualTableName = rs.getString("TABLE_NAME");
                if (actualTableName.equalsIgnoreCase(tableName)) {
                    return actualTableName;
                }
            }
        }
        throw exception(DATABASE_ADMIN_TABLE_NOT_EXISTS);
    }

    private Map<String, Object> validateValues(DatabaseAdminTableDetailRespVO table, Map<String, Object> sourceValues,
                                               boolean create) {
        Map<String, DatabaseAdminColumnRespVO> columns = table.getColumns().stream()
                .collect(Collectors.toMap(DatabaseAdminColumnRespVO::getName, column -> column, (a, b) -> a, LinkedHashMap::new));
        Map<String, Object> values = new LinkedHashMap<>();
        sourceValues.forEach((columnName, value) -> {
            DatabaseAdminColumnRespVO column = columns.get(columnName);
            if (column == null) {
                throw exception(DATABASE_ADMIN_COLUMN_NOT_EXISTS);
            }
            if (Boolean.TRUE.equals(column.getSensitive())) {
                throw exception(DATABASE_ADMIN_SENSITIVE_COLUMN, columnName);
            }
            if (!Boolean.TRUE.equals(column.getEditable())) {
                if (create && Boolean.TRUE.equals(column.getAutoIncrement())) {
                    return;
                }
                throw exception(DATABASE_ADMIN_COLUMN_READONLY, columnName);
            }
            values.put(columnName, DatabaseAdminValueCodec.convert(column, value));
        });
        if (!create && values.isEmpty()) {
            throw exception(DATABASE_ADMIN_COLUMN_NOT_EXISTS);
        }
        return values;
    }

    private QueryClause buildKeywordWhere(Connection connection, List<DatabaseAdminColumnRespVO> columns,
                                          String keyword) throws SQLException {
        if (StrUtil.isBlank(keyword)) {
            return new QueryClause("", Collections.emptyList());
        }
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (DatabaseAdminColumnRespVO column : columns) {
            if (Boolean.TRUE.equals(column.getSensitive()) || !isTextColumn(column.getJdbcType())) {
                continue;
            }
            conditions.add("LOWER(" + quoteIdentifier(connection, column.getName()) + ") LIKE ?");
            parameters.add("%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }
        if (conditions.isEmpty()) {
            return new QueryClause("", Collections.emptyList());
        }
        return new QueryClause(" WHERE " + String.join(" OR ", conditions), parameters);
    }

    private Map<String, Object> readRow(ResultSet rs, ResultSetMetaData metaData,
                                        List<DatabaseAdminColumnRespVO> columns) throws SQLException {
        Map<String, DatabaseAdminColumnRespVO> columnMap = columns.stream()
                .collect(Collectors.toMap(DatabaseAdminColumnRespVO::getName, column -> column));
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnLabel(i);
            DatabaseAdminColumnRespVO column = columnMap.get(columnName);
            Object value = column == null ? rs.getString(i) : DatabaseAdminValueCodec.read(rs, i, column);
            row.put(columnName, column != null && Boolean.TRUE.equals(column.getSensitive()) && value != null ? "******" : value);
        }
        return row;
    }

    private static int bindParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            ps.setObject(index++, parameter);
        }
        return index;
    }

    private static String getSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException ex) {
            return null;
        }
    }

    private static String getSchemaPattern(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            return null;
        }
        return getSchema(connection);
    }

    private static String quoteIdentifier(Connection connection, String identifier) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (StrUtil.isBlank(quote)) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private static String literalPattern(DatabaseMetaData metadata, String name) throws SQLException {
        String escape = metadata.getSearchStringEscape();
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static boolean isSensitiveColumn(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        return SENSITIVE_COLUMN_TOKENS.stream().anyMatch(normalized::contains);
    }

    private static boolean isTextColumn(Integer jdbcType) {
        return Objects.equals(jdbcType, Types.CHAR)
                || Objects.equals(jdbcType, Types.VARCHAR)
                || Objects.equals(jdbcType, Types.LONGVARCHAR)
                || Objects.equals(jdbcType, Types.NCHAR)
                || Objects.equals(jdbcType, Types.NVARCHAR)
                || Objects.equals(jdbcType, Types.LONGNVARCHAR);
    }

    static String normalizeRemark(String remark) {
        String value = StrUtil.nullToEmpty(remark);
        if (value.isEmpty() || !containsMojibakeMarker(value)) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return countHanCharacters(repaired) > countHanCharacters(value) ? repaired : value;
    }

    private static boolean containsMojibakeMarker(String value) {
        return value.indexOf('�') >= 0
                || value.indexOf('Ã') >= 0
                || value.indexOf('Â') >= 0
                || value.indexOf('å') >= 0
                || value.indexOf('æ') >= 0
                || value.indexOf('è') >= 0
                || value.indexOf('é') >= 0
                || value.indexOf('ç') >= 0
                || value.indexOf('î') >= 0
                || value.indexOf('ï') >= 0
                || value.indexOf('ö') >= 0
                || value.indexOf('ü') >= 0;
    }

    private static int countHanCharacters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    private <T> T execute(Long dataSourceConfigId, String operation, String tableName, SqlCallback<T> callback) {
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        if (config == null) {
            throw exception(DATA_SOURCE_CONFIG_NOT_EXISTS);
        }
        validateSupportedDatabase(config.getUrl());
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword())) {
            try {
                return callback.execute(connection);
            } catch (ServiceException ex) {
                throw ex;
            } catch (SQLException ex) {
                logSqlFailure(dataSourceConfigId, operation, tableName, ex);
                throw sqlFailure(ex);
            }
        } catch (SQLException ex) {
            logSqlFailure(dataSourceConfigId, operation, tableName, ex);
            if (isAccessDenied(ex)) {
                throw exception(DATABASE_ADMIN_ACCESS_DENIED);
            }
            throw exception(DATABASE_ADMIN_CONNECTION_FAIL);
        }
    }

    private static void logSqlFailure(Long configId, String operation, String table, SQLException ex) {
        String safeTable = table != null && table.matches("[\\p{L}\\p{N}_$]{1,64}") ? table : "-";
        log.warn("[databaseAdmin] operation={} dataSource={} table={} sqlState={} vendorCode={} traceId={}",
                operation, configId, safeTable, ex.getSQLState(), ex.getErrorCode(), TracerUtils.getTraceId());
    }

    private static boolean isAccessDenied(SQLException ex) {
        return "28000".equals(ex.getSQLState()) || Set.of(1044, 1045, 1142, 1143, 1227).contains(ex.getErrorCode());
    }

    static ServiceException sqlFailure(SQLException ex) {
        String state = StrUtil.nullToEmpty(ex.getSQLState());
        int code = ex.getErrorCode();
        if (isAccessDenied(ex)) {
            return exception(DATABASE_ADMIN_ACCESS_DENIED);
        }
        if (code == 1062 || state.equals("23505")) {
            return exception(DATABASE_ADMIN_DUPLICATE);
        }
        if (Set.of(1048, 1364).contains(code) || state.equals("23502")) {
            return exception(DATABASE_ADMIN_NOT_NULL);
        }
        if (Set.of(1451, 1452).contains(code) || Set.of("23503", "23506").contains(state)) {
            return exception(DATABASE_ADMIN_REFERENCE);
        }
        if (Set.of(1264, 1265, 1406).contains(code) || Set.of("22001", "22003").contains(state)) {
            return exception(DATABASE_ADMIN_DATA_LIMIT);
        }
        if (Set.of(1292, 1366, 3140, 3141).contains(code) || state.startsWith("22")) {
            return exception(DATABASE_ADMIN_VALUE_INVALID, "输入字段");
        }
        if (state.startsWith("23") || code == 3819) {
            return exception(DATABASE_ADMIN_CONSTRAINT);
        }
        return exception(DATABASE_ADMIN_EXECUTE_FAIL);
    }

    private static void validateSupportedDatabase(String url) {
        String normalized = StrUtil.nullToEmpty(url).toLowerCase(Locale.ROOT);
        if (!normalized.contains(":mysql:") && !normalized.contains(":mariadb:") && !normalized.contains(":h2:")) {
            throw exception(DATABASE_ADMIN_UNSUPPORTED_DATABASE);
        }
    }

    @FunctionalInterface
    private interface SqlCallback<T> {

        T execute(Connection connection) throws SQLException;

    }

    private record QueryClause(String sql, List<Object> parameters) {
    }

}
