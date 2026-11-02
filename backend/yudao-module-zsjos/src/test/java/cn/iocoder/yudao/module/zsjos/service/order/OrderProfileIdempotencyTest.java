package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderProfileIdempotencyTest {
    @Test void changedProfileOnLostResponseRetryReplaysOrderWithoutMutatingRequest() {
        var req=new SalesOrderSubmitReqVO();req.setStudentName("fixture");req.setIdempotencyKey("same-command");
        String fingerprint=DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(10L,99L,"sales","reason",req)));
        var existing=new SalesOrderDO();existing.setId(30L);existing.setPersonId(10L);existing.setSubmitterUserId(99L);
        existing.setSubmitterCenterType("sales");existing.setSubmissionRequestFingerprint(fingerprint);
        var mapper=mock(SalesOrderMapper.class);when(mapper.selectByIdempotencyKey("same-command")).thenReturn(existing);
        var service=new SalesOrderServiceImpl();ReflectionTestUtils.setField(service,"orderMapper",mapper);
        var choice=new OrderProfileUpdateReqVO();choice.setFingerprint("new-snapshot-after-success");choice.setFields(List.of());req.setProfileUpdate(choice);
        Long replay=ReflectionTestUtils.invokeMethod(service,"createRepurchase",10L,null,99L,99L,"reason",req,true,"sales",null,null);
        assertEquals(30L,replay);assertSame(choice,req.getProfileUpdate());
        req.setStudentName("different-business-request");
        assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"createRepurchase",10L,null,99L,99L,"reason",req,true,"sales",null,null));
    }
}
