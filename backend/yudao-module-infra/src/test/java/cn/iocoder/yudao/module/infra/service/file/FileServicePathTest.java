package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServicePathTest {
    @Mock FileMapper fileMapper;
    @Mock FileConfigService fileConfigService;
    @InjectMocks FileServiceImpl service;
    @TempDir Path temporary;

    @Test void storesMetadataUsingTheRepeatableSourceAndLeavesSourceOwnershipToCaller() throws Exception {
        Path source = Files.writeString(temporary.resolve("source.txt"), "synthetic content");
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(1L);
        when(client.uploadFile(eq(source), anyString(), eq("text/plain"))).thenReturn("https://files.invalid/object");
        var row = service.createFileInfoFromPath(source, "transcript.txt", "zsjos/positioning/1", "text/plain");
        assertEquals(Files.size(source), row.getSize());
        assertEquals("transcript.txt", row.getName());
        assertEquals(1L, row.getConfigId());
        assertTrue(Files.exists(source));
        verify(client, never()).upload(any(byte[].class), anyString(), anyString());
        verify(fileMapper).insert(row);
    }

    @Test void metadataFailureRemovesOnlyTheNewlyUploadedStorageObject() throws Exception {
        Path source = Files.writeString(temporary.resolve("source.txt"), "synthetic content");
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(1L);
        when(client.uploadFile(eq(source), anyString(), eq("text/plain"))).thenReturn("https://files.invalid/object");
        doThrow(new IllegalStateException("metadata failed")).when(fileMapper).insert(any(FileDO.class));
        assertThrows(IllegalStateException.class, () -> service.createFileInfoFromPath(source, "file.txt", "test", "text/plain"));
        verify(client).delete(argThat(path -> path.startsWith("test/")));
        assertTrue(Files.exists(source));
    }
    @Test void uploadFailureCleansItsGeneratedPathWithoutLosingTheOriginalError() throws Exception {
        Path source = Files.writeString(temporary.resolve("source.txt"), "synthetic content");
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        var failure = new java.io.IOException("partial upload failed");
        when(client.uploadFile(eq(source), anyString(), eq("text/plain"))).thenThrow(failure);
        assertSame(failure, assertThrows(java.io.IOException.class,
                () -> service.createFileInfoFromPath(source, "file.txt", "test", "text/plain")));
        verify(client).delete(argThat(path -> path.startsWith("test/")));
        verifyNoInteractions(fileMapper);
        assertTrue(Files.exists(source));
    }

}
