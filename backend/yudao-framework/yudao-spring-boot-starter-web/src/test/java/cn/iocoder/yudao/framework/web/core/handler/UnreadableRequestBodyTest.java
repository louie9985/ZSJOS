package cn.iocoder.yudao.framework.web.core.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import static org.junit.jupiter.api.Assertions.*;

class UnreadableRequestBodyTest {
    record GiftRequest(java.util.List<String> giftItems) {}

    @Test void jsonConverterRejectsStringInsteadOfArrayAndReturnsParameterError() throws Exception {
        var converter = new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter();
        var input = new MockHttpInputMessage("{\"giftItems\":\"[private-value]\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        input.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var error = assertThrows(HttpMessageNotReadableException.class, () -> converter.read(GiftRequest.class, input));
        var result = new GlobalExceptionHandler("test", null).methodArgumentTypeInvalidFormatExceptionHandler(error);
        assertEquals(400, result.getCode());
        assertFalse(result.getMsg().contains("private-value"));
        var valid = new MockHttpInputMessage("{\"giftItems\":[\"gift-code\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        valid.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        assertEquals(java.util.List.of("gift-code"), ((GiftRequest) converter.read(GiftRequest.class, valid)).giftItems());
    }

    @Test void malformedAndMismatchedBodiesAreBadRequestsWithoutPayloadDisclosure() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("test", null);
        for (String detail : new String[] {"Cannot deserialize private-value", "Required request body is missing", "Unexpected token private-value"}) {
            var response = handler.methodArgumentTypeInvalidFormatExceptionHandler(
                    new HttpMessageNotReadableException(detail, new MockHttpInputMessage(new byte[0])));
            assertEquals(400, response.getCode());
            assertFalse(response.getMsg().contains("private-value"));
        }
    }
}
