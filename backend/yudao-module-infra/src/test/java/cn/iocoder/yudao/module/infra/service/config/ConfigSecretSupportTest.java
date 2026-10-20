package cn.iocoder.yudao.module.infra.service.config;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import cn.iocoder.yudao.module.infra.convert.config.ConfigConvert;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigSecretSupportTest {
    ConfigSecretSupport support;
    SecurityFrameworkService security;
    @BeforeEach void setup() {
        support = new ConfigSecretSupport(); security = mock(SecurityFrameworkService.class);
        ReflectionTestUtils.setField(support,"encryptionKey","0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(support,"security",security);
    }
    @Test void deniesWithoutPermission() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> support.prepare(secret(),null));
    }
    @Test void encryptsMasksAndPreservesOnBlankUpdate() {
        when(security.hasPermission("infra:config:secret-update")).thenReturn(true);
        var row=secret(); String raw=row.getValue(); support.prepare(row,null);
        assertNotEquals(raw,row.getValue()); assertEquals(raw,support.decrypt(row.getValue()));
        assertFalse(row.getVisible());
        var view=ConfigConvert.INSTANCE.convert(row); assertEquals("",view.getValue()); assertTrue(view.getConfigured());
        var blank=secret().setValue("").setSensitive(false);support.prepare(blank,row);
        assertNull(blank.getValue());assertTrue(blank.getSensitive());
        assertEquals("",ConfigConvert.INSTANCE.convertList(java.util.List.of(row)).getFirst().getValue());
    }
    @Test void cannotConvertPlaintextWithoutNewValueOrRenameSecret() {
        when(security.hasPermission("infra:config:secret-update")).thenReturn(true);
        assertThrows(RuntimeException.class,()->support.prepare(secret().setValue(""),secret().setSensitive(false)));
        assertThrows(RuntimeException.class,()->support.prepare(secret().setConfigKey("other"),secret()));
    }
    @Test void missingEncryptionKeyFailsClosedAndMaskCannotBeStored() {
        when(security.hasPermission("infra:config:secret-update")).thenReturn(true);
        ReflectionTestUtils.setField(support,"encryptionKey","");
        assertThrows(RuntimeException.class,()->support.prepare(secret(),null));
        assertThrows(RuntimeException.class,()->support.prepare(secret().setValue("********"),null));
    }
    private ConfigDO secret() {return new ConfigDO().setConfigKey("zsjos.points-redemption.api-key")
            .setValue("a".repeat(64)).setSensitive(true).setVisible(true);}
}
