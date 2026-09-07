package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminColumnRespVO;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.HexFormat;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

/** Lossless wire values and column-aware JDBC conversion, local to the database editor. */
final class DatabaseAdminValueCodec {
    private static final Set<Integer> BINARY_TYPES = Set.of(
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB, Types.BIT);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private DatabaseAdminValueCodec() { }

    static String kind(DatabaseAdminColumnRespVO column) {
        String type = column.getTypeName().toUpperCase(Locale.ROOT);
        if (type.equals("JSON")) {
            return "json";
        }
        return switch (column.getJdbcType()) {
            case Types.BOOLEAN -> "boolean";
            case Types.BIT -> Long.valueOf(1).equals(column.getColumnSize()) ? "boolean" : "readonly";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> "integer";
            case Types.NUMERIC, Types.DECIMAL -> "decimal";
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> "float";
            case Types.DATE -> "date";
            case Types.TIME -> "time";
            case Types.TIMESTAMP -> "datetime";
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                 Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "text";
            default -> "readonly";
        };
    }

    static Object read(ResultSet rs, int index, DatabaseAdminColumnRespVO column) throws SQLException {
        if ("boolean".equals(column.getValueKind())) {
            boolean value = rs.getBoolean(index);
            return rs.wasNull() ? null : value;
        }
        if (BINARY_TYPES.contains(column.getJdbcType())) {
            byte[] value = rs.getBytes(index);
            return value == null ? null : "0x" + HexFormat.of().formatHex(value);
        }
        // JDBC textual reads avoid JS number rounding and global epoch-millisecond serializers.
        return rs.getString(index);
    }


    static Object convert(DatabaseAdminColumnRespVO column, Object value) {
        if (value == null) {
            if (!Boolean.TRUE.equals(column.getNullable())) {
                throw exception(DATABASE_ADMIN_NOT_NULL);
            }
            return null;
        }
        try {
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
            }
            String text = value.toString();
            return switch (column.getValueKind()) {
                case "boolean" -> switch (text) {
                    case "true", "1" -> true;
                    case "false", "0" -> false;
                    default -> throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                };
                case "integer" -> integer(column, text);
                case "decimal" -> decimal(column, text);
                case "float" -> {
                    double number = Double.parseDouble(text);
                    if (!Double.isFinite(number)) {
                        throw exception(DATABASE_ADMIN_DATA_LIMIT);
                    }
                    yield number;
                }
                case "date" -> {
                    if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                    }
                    LocalDate.parse(text);
                    yield text;
                }
                case "datetime" -> {
                    if (!text.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?")) {
                        throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                    }
                    LocalDateTime.parse(text.replace(' ', 'T'));
                    validateFraction(column, text);
                    yield text.replace('T', ' ');
                }
                case "time" -> {
                    // MySQL TIME also represents signed durations up to 838:59:59.
                    if (!text.matches("-?\\d{2,3}:[0-5]\\d:[0-5]\\d(\\.\\d{1,9})?")) {
                        throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                    }
                    if (Integer.parseInt(text.split(":")[0].replace("-", "")) > 838) {
                        throw exception(DATABASE_ADMIN_DATA_LIMIT);
                    }
                    validateFraction(column, text);
                    yield text;
                }
                case "text", "json" -> {
                    if (!(value instanceof String)) {
                        throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                    }
                    if (hasBoundedCharacterLength(column)
                            && column.getColumnSize() != null
                            && text.codePointCount(0, text.length()) > column.getColumnSize()) {
                        throw exception(DATABASE_ADMIN_DATA_LIMIT);
                    }
                    if ("json".equals(column.getValueKind()) && (text.isBlank() || JSON.readTree(text) == null)) {
                        throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
                    }
                    yield text;
                }
                default -> throw exception(DATABASE_ADMIN_COLUMN_READONLY, column.getName());
            };
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Parser messages may include the submitted content; never propagate them.
            throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
        }
    }

    private static boolean hasBoundedCharacterLength(DatabaseAdminColumnRespVO column) {
        String type = column.getTypeName().toUpperCase(Locale.ROOT);
        return type.equals("CHAR") || type.equals("VARCHAR") || type.equals("NCHAR") || type.equals("NVARCHAR");
    }

    private static BigDecimal integer(DatabaseAdminColumnRespVO column, String text) {
        if (!text.matches("[+-]?\\d+")) {
            throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
        }
        BigInteger value = new BigInteger(text);
        int bits = switch (column.getJdbcType()) {
            case Types.TINYINT -> 8;
            case Types.SMALLINT -> 16;
            case Types.INTEGER -> column.getTypeName().toUpperCase(Locale.ROOT).startsWith("MEDIUMINT") ? 24 : 32;
            default -> 64;
        };
        boolean unsigned = column.getTypeName().toUpperCase(Locale.ROOT).contains("UNSIGNED");
        BigInteger min = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw exception(DATABASE_ADMIN_DATA_LIMIT);
        }
        return new BigDecimal(value);
    }

    private static BigDecimal decimal(DatabaseAdminColumnRespVO column, String text) {
        if (!text.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")) {
            throw exception(DATABASE_ADMIN_VALUE_INVALID, column.getName());
        }
        BigDecimal value = new BigDecimal(text);
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = column.getDecimalDigits() == null ? 0 : column.getDecimalDigits();
        if ((column.getTypeName().toUpperCase(Locale.ROOT).contains("UNSIGNED") && value.signum() < 0)
                || normalized.scale() > scale
                || (column.getColumnSize() != null
                && (normalized.signum() == 0 ? 0 : Math.max(0, normalized.precision() - normalized.scale())) > column.getColumnSize() - scale)) {
            throw exception(DATABASE_ADMIN_DATA_LIMIT);
        }
        return value;
    }

    private static void validateFraction(DatabaseAdminColumnRespVO column, String text) {
        int dot = text.indexOf('.');
        if (dot >= 0 && column.getDecimalDigits() != null
                && text.substring(dot + 1).replaceFirst("0+$", "").length() > column.getDecimalDigits()) {
            throw exception(DATABASE_ADMIN_DATA_LIMIT);
        }
    }

    static void bind(PreparedStatement ps, int index, DatabaseAdminColumnRespVO column, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, column.getJdbcType());
        } else if (value instanceof Boolean bool) {
            ps.setBoolean(index, bool);
        } else if (value instanceof BigDecimal decimal) {
            ps.setBigDecimal(index, decimal);
        } else if (value instanceof Double number) {
            ps.setDouble(index, number);
        } else {
            // Validated temporal strings preserve TIME durations and microseconds without JVM timezone conversion.
            ps.setString(index, (String) value);
        }
    }
}
