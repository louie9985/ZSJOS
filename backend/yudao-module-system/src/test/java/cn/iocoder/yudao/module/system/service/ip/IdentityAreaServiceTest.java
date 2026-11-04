package cn.iocoder.yudao.module.system.service.ip;

import cn.iocoder.yudao.module.system.dal.dataobject.ip.IdentityAreaDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IdentityAreaServiceTest {
    private IdentityAreaDO record(String name, String status) {
        var row = new IdentityAreaDO(); row.setProvinceName("测试省"); row.setCityName("测试市");
        row.setName(name); row.setRecordStatus(status); row.setDataVersion("test"); return row;
    }
    @Test void historicSplitRetainsOriginalInsteadOfChoosingSuccessor() {
        var row = record("原县", "弃用"); row.setSuccessorCodes("420602;420606;420607");
        var result = IdentityAreaService.resolve("420621", List.of(row));
        assertEquals("HISTORICAL", result.getStatus());
        assertEquals("测试省 / 测试市 / 原县", result.getLabel());
    }
    @Test void multipleNamesRequireClarificationEvenWhenOneIsCurrent() {
        var result = IdentityAreaService.resolve("110101", List.of(record("旧区", "变更"), record("新区", "在用")));
        assertEquals("AMBIGUOUS", result.getStatus()); assertNull(result.getLabel());
    }
    @Test void missingCodeIsDistinctFromHistorical() {
        assertEquals("UNRECOGNIZED", IdentityAreaService.resolve("999999", List.of()).getStatus());
    }
    @Test void identicalLabelsAcrossPeriodsAreRecognizable() {
        assertEquals("CURRENT", IdentityAreaService.resolve("110101", List.of(record("同区", "变更"), record("同区", "在用"))).getStatus());
    }
}
