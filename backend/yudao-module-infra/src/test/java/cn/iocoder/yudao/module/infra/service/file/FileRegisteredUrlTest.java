package cn.iocoder.yudao.module.infra.service.file;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class FileRegisteredUrlTest {
    @Test void exactRegisteredUrlOnly() {
        var mapper=mock(FileMapper.class); var service=new FileServiceImpl();
        ReflectionTestUtils.setField(service,"fileMapper",mapper);
        var file=FileDO.builder().id(3L).url("https://files.test/Book.xlsx").configId(2L).path("Book.xlsx").build();
        when(mapper.selectByRegisteredUrl(anyString())).thenReturn(List.of(file));
        assertNull(service.getRegisteredFileByUrl("https://files.test/book.xlsx"));
        assertSame(file,service.getRegisteredFileByUrl(file.getUrl()));
        assertNull(service.getRegisteredFileByUrl("http://127.0.0.1/private"));
        assertNull(service.getRegisteredFileByUrl(null));
    }
}
