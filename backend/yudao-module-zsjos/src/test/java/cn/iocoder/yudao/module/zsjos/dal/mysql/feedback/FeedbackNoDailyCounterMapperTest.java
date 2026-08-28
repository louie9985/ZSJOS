package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackNoDailyCounterMapperTest {

    @Test
    void reservationMustNotUseConnectionLastInsertId() throws Exception {
        Method reserve = FeedbackNoDailyCounterMapper.class.getMethod(
                "reserve", Long.class, LocalDate.class, String.class, long.class);
        String sql = String.join(" ", reserve.getAnnotation(Insert.class).value());

        assertFalse(sql.contains("LAST_INSERT_ID"));
        assertTrue(sql.contains("GREATEST(current_value+1,#{minimumValue})"));
    }

    @Test
    void reservedValueMustBeReadFromTheBusinessCounterRow() throws Exception {
        Method select = FeedbackNoDailyCounterMapper.class.getMethod(
                "selectReservedValue", Long.class, LocalDate.class, String.class);
        String sql = String.join(" ", select.getAnnotation(Select.class).value());

        assertTrue(sql.contains("SELECT current_value"));
        assertTrue(sql.contains("tenant_id=#{tenantId}"));
        assertTrue(sql.contains("sequence_date=#{date}"));
        assertTrue(sql.contains("feedback_type=#{type}"));
        assertTrue(sql.contains("FOR UPDATE"));
    }
}
