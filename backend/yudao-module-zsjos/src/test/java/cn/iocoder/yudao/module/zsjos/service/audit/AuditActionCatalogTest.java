package cn.iocoder.yudao.module.zsjos.service.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditActionCatalogTest {
    @Test
    void onlyRegisteredCategoryActionPairsAreAccepted() {
        assertTrue(AuditActionCatalog.contains("export", "export.create"));
        assertTrue(AuditActionCatalog.contains("impersonation", "impersonation.read"));
        assertFalse(AuditActionCatalog.contains("export", "free text"));
    }
}
