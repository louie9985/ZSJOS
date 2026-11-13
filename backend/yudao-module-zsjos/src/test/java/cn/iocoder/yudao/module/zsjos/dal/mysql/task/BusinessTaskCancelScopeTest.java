package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BusinessTaskCancelScopeTest {
    @Test
    void scopedCancellationIncludesIndexPrefixAndPendingPredicate() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BusinessTaskDO.class);
        BusinessTaskMapper mapper = mock(BusinessTaskMapper.class, CALLS_REAL_METHODS);
        mapper.cancelPending("lead", "lead_follow_up_reminder", 7L, null, LocalDateTime.now(), "done");
        ArgumentCaptor<LambdaUpdateWrapper<BusinessTaskDO>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("biz_type ="));
        assertTrue(sql.contains("biz_id ="));
        assertTrue(sql.contains("task_type ="));
        assertTrue(sql.contains("status ="));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("lead"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("pending"));
    }
}
