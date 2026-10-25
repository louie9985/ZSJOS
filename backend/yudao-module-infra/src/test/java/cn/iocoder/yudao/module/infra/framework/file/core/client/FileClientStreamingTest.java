package cn.iocoder.yudao.module.infra.framework.file.core.client;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileClientStreamingTest {
    @TempDir Path temporary;

    private Path source() throws IOException {
        return Files.writeString(temporary.resolve("source.txt"), "repeatable synthetic content", StandardCharsets.UTF_8);
    }

    private S3FileClient s3(S3Client transport) {
        var config=new S3FileClientConfig();config.setBucket("synthetic-bucket");
        config.setDomain("https://files.example.invalid");config.setEnablePublicAccess(true);
        var client=new S3FileClient(7L,config);
        ReflectionTestUtils.setField(client,"client",transport);
        return client;
    }

    @Test void s3UsesARepeatableFileBodyForTransportRetriesWithoutOwningSource() throws Exception {
        Path source=source();byte[] expected=Files.readAllBytes(source);
        S3Client transport=mock(S3Client.class);
        when(transport.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest request=call.getArgument(0);RequestBody body=call.getArgument(1);
            assertEquals("synthetic-bucket",request.bucket());assertEquals("folder/file.txt",request.key());
            assertEquals("text/plain",request.contentType());assertEquals((long)expected.length,request.contentLength());
            assertEquals((long)expected.length,body.contentLength());
            try(var first=body.contentStreamProvider().newStream()){assertArrayEquals(expected,first.readAllBytes());}
            try(var retry=body.contentStreamProvider().newStream()){assertArrayEquals(expected,retry.readAllBytes());}
            return PutObjectResponse.builder().build();
        });
        assertEquals("https://files.example.invalid/folder/file.txt",s3(transport).uploadFile(source,"folder/file.txt","text/plain"));
        assertTrue(Files.exists(source));verify(transport,times(1)).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }

    @Test void s3TransportFailurePropagatesAndLeavesRepeatableSourceToCaller() throws Exception {
        Path source=source();S3Client transport=mock(S3Client.class);
        var failure=new IllegalStateException("synthetic storage failure");
        when(transport.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenThrow(failure);
        assertSame(failure,assertThrows(IllegalStateException.class,()->s3(transport).uploadFile(source,"folder/file.txt","text/plain")));
        assertEquals("repeatable synthetic content",Files.readString(source));
    }

    @Test void mysqlUsesServerPreparedStreamAndClosesAllOwnedResources() throws Exception {
        dbUpload(true,false);
    }

    @Test void databaseFailureStillClosesInputStatementAndBorrowedConnection() throws Exception {
        dbUpload(true,true);
    }

    @Test void nonMysqlJdbcFallbackStillPassesAStreamAndClosesResources() throws Exception {
        dbUpload(false,false);
    }

    private void dbUpload(boolean mysql,boolean fail) throws Exception {
        Path source=source();byte[] expected=Files.readAllBytes(source);
        DataSource dataSource=mock(DataSource.class);Connection connection=mock(Connection.class);
        JdbcConnection nativeConnection=mock(JdbcConnection.class);PreparedStatement statement=mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isWrapperFor(JdbcConnection.class)).thenReturn(mysql);
        if(mysql){when(connection.unwrap(JdbcConnection.class)).thenReturn(nativeConnection);when(nativeConnection.serverPrepareStatement(anyString())).thenReturn(statement);}
        else when(connection.prepareStatement(anyString())).thenReturn(statement);
        AtomicReference<InputStream> stream=new AtomicReference<>();
        doAnswer(call->{stream.set(call.getArgument(1));assertEquals((long)expected.length,call.getArgument(2,Long.class));return null;})
                .when(statement).setBinaryStream(eq(3),any(InputStream.class),anyLong());
        var failure=new SQLException("synthetic execute failure");
        when(statement.executeUpdate()).thenAnswer(call->{assertArrayEquals(expected,stream.get().readAllBytes());if(fail)throw failure;return 1;});
        var config=new DBFileClientConfig();config.setDomain("https://files.example.invalid");
        var client=new DBFileClient(7L,config);
        try(var spring=mockStatic(SpringUtil.class);var security=mockStatic(SecurityFrameworkUtils.class)){
            spring.when(()->SpringUtil.getBean(DataSource.class)).thenReturn(dataSource);
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            if(fail)assertSame(failure,assertThrows(SQLException.class,()->client.uploadFile(source,"folder/file.txt","text/plain")));
            else assertTrue(client.uploadFile(source,"folder/file.txt","text/plain").contains("file.txt"));
        }
        if(mysql){verify(nativeConnection).serverPrepareStatement(argThat(sql->sql.contains("infra_file_content")));verify(connection,never()).prepareStatement(anyString());}
        verify(statement).setLong(1,7L);verify(statement).setString(2,"folder/file.txt");
        verify(statement).setString(4,"9");verify(statement).setString(5,"9");
        verify(statement).close();verify(connection).close();
        assertNotNull(stream.get());assertThrows(IOException.class,()->stream.get().read());assertTrue(Files.exists(source));
        verify(statement,never()).setBytes(anyInt(),any(byte[].class));
    }
}
