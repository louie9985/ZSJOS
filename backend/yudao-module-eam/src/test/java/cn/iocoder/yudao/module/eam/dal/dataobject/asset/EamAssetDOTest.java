package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EamAssetDOTest {

    @Test
    void fileUrlsShouldUseJsonTypeHandler() throws NoSuchFieldException {
        TableField tableField = EamAssetDO.class.getDeclaredField("fileUrls").getAnnotation(TableField.class);

        assertEquals(JacksonTypeHandler.class, tableField.typeHandler());
    }

}
