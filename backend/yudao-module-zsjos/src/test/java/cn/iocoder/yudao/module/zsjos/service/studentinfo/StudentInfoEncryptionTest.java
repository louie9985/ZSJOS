package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentInfoEncryptionTest {
    @Test void storageIsCiphertextAndReadsRoundTripUtf8() throws Exception {
        Object previous=ReflectionTestUtils.getField(EncryptTypeHandler.class,"aes");
        ReflectionTestUtils.setField(EncryptTypeHandler.class,"aes",null);
        try (var spring=mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.password"))
                    .thenReturn(UUID.randomUUID().toString().replace("-",""));
            var handler=new EncryptTypeHandler(); var statement=mock(PreparedStatement.class);
            String text="加密测试内容";
            handler.setNonNullParameter(statement,1,text,JdbcType.VARCHAR);
            var encrypted=ArgumentCaptor.forClass(String.class);
            verify(statement).setString(eq(1),encrypted.capture());
            assertNotEquals(text,encrypted.getValue()); assertFalse(encrypted.getValue().contains(text));
            var result=mock(ResultSet.class); when(result.getString("value_text")).thenReturn(encrypted.getValue());
            assertEquals(text,handler.getNullableResult(result,"value_text"));
            assertNull(handler.getNullableResult(result,"missing"));
        } finally { ReflectionTestUtils.setField(EncryptTypeHandler.class,"aes",previous); }
    }
}
