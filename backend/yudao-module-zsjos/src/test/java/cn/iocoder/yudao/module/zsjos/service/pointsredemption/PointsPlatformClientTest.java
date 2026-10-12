package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PointsPlatformClientTest {
    @Test void rejectsUntrustedEndpointsAndMissingCredentials() {
        var client=new PointsPlatformClient();var config=mock(ConfigApi.class);
        ReflectionTestUtils.setField(client,"allowedOrigins","https://zsj.nbility.ai");ReflectionTestUtils.setField(client,"configs",config);
        assertDoesNotThrow(()->client.validateBase("https://zsj.nbility.ai/api/partner/v1"));
        for(String url:List.of("http://zsj.nbility.ai/api/partner/v1","https://attacker.test/api/partner/v1","https://x@zsj.nbility.ai/api/partner/v1","https://zsj.nbility.ai/api/partner/v1?key=secret"))
            assertThrows(RuntimeException.class,()->client.validateBase(url));
        assertThrows(RuntimeException.class,client::requireConfigured);
    }
    @Test void lostResponseRecoversExistingCodeAndCreationBodyStaysIdentical() {
        List<String> calls=new ArrayList<>();List<String> bodies=new ArrayList<>();
        var existing=new JsonMapper().readTree("{\"orderId\":\"ZSJOS-TEST\",\"codes\":[]}");
        var client=new PointsPlatformClient(){
            boolean exists;
            @Override JsonNode send(String base,String path,String body) {
                calls.add(path);
                if(body==null){if(exists)return existing;throw new PlatformFailure("not_found",false,0);}
                bodies.add(body);exists=true;throw new PlatformFailure("network_error",true,0);
            }
        };
        var row=new PointsRequestDO();row.setOrderId("ZSJOS-TEST");row.setRequestNo("JFTEST");row.setCreditsAmount(5000);row.setAttempts(1);
        assertThrows(PointsPlatformClient.PlatformFailure.class,()->client.issue(row));
        assertSame(existing,client.issue(row));assertEquals(3,calls.size());assertEquals(1,bodies.size());
        var body=new JsonMapper().readTree(bodies.getFirst());assertEquals(1,body.path("quantity").asInt());assertEquals(5000,body.path("creditsAmount").asInt());
        assertEquals("ZSJOS-TEST",body.path("orderId").asText());assertTrue(body.path("expiresAt").isNull());
    }
    @Test void invalidExternalCreditAmountOrExpiryCannotBeSaved() {
        var row=new PointsRequestDO();row.setCreditsAmount(5000);
        var valid=new JsonMapper().readTree("{\"id\":\"code-id\",\"code\":\"KM-0123456789ABCDEF\",\"creditsAmount\":5000,\"status\":\"available\",\"expiresAt\":null}");
        assertDoesNotThrow(()->PointsIssuanceService.validateCode(valid,row));
        row.setCreditsAmount(100);assertThrows(RuntimeException.class,()->PointsIssuanceService.validateCode(valid,row));
        row.setCreditsAmount(5000);row.setExpiresAt(java.time.LocalDateTime.now());assertThrows(RuntimeException.class,()->PointsIssuanceService.validateCode(valid,row));
    }
}
