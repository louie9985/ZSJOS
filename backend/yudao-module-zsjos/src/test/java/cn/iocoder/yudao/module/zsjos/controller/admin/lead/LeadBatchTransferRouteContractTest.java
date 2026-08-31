package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class LeadBatchTransferRouteContractTest {

    @Test
    void singleTransferRouteOnlyAcceptsNumericLeadIds() throws NoSuchMethodException {
        PostMapping mapping = LeadQualificationController.class
                .getMethod("transfer", Long.class, LeadTransferReqVO.class)
                .getAnnotation(PostMapping.class);

        assertArrayEquals(new String[]{"/{id:\\d+}/transfer"}, mapping.value());
    }
}
