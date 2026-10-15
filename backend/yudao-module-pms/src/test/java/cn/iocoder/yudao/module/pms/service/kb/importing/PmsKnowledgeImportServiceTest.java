package cn.iocoder.yudao.module.pms.service.kb.importing;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.controller.admin.kb.importing.vo.PmsKnowledgeImportVO.*;
import cn.iocoder.yudao.module.pms.controller.admin.kb.library.vo.library.PmsKnowledgeLibrarySaveReqVO;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.importing.*;
import cn.iocoder.yudao.module.pms.service.kb.library.*;
import cn.iocoder.yudao.module.pms.service.kb.content.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Import({PmsKnowledgeImportService.class,PmsKnowledgeImportPolicy.class})
@Sql("classpath:sql/knowledge_import.sql")
public class PmsKnowledgeImportServiceTest extends BaseDbUnitTest {
    @Resource PmsKnowledgeImportService service;
    @Resource PmsKnowledgeImportTaskMapper tasks;
    @Resource PmsKnowledgeImportItemMapper items;
    @Resource cn.iocoder.yudao.module.pms.dal.mysql.kb.content.PmsKnowledgeFolderMapper folderMapper;
    @Resource cn.iocoder.yudao.module.pms.dal.mysql.kb.content.PmsKnowledgeDocumentMapper documentMapper;
    @MockitoBean PmsKnowledgeLibraryService libraryService;
    @MockitoBean PmsKnowledgeLibraryMemberService members;
    @MockitoBean PmsKnowledgeContentPermissionService permissions;
    @MockitoBean PmsKnowledgeFolderService folderService;
    @MockitoBean PmsKnowledgeDocumentService documentService;
    @MockitoBean PermissionApi permissionApi;
    @MockitoBean FileApi fileApi;
    @BeforeEach void setup() {
        when(permissionApi.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
        when(libraryService.createLibrary(any(),eq(7L))).thenReturn(43210L);
        when(documentService.createDocument(any(),eq(7L))).thenReturn(12345L);
        var info=new FileInfoRespDTO();info.setId(987L);info.setUrl("/file/example.txt");
        when(fileApi.createFileInfo(any(),anyString(),anyString(),isNull())).thenReturn(info);
    }
    private Entry entry(String path,String text) {
        var e=new Entry();e.setRelativePath(path);e.setFileSize((long)text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        e.setLastModified(1L);e.setDigest(PmsKnowledgeImportService.hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return e;
    }
    private Request request(Entry... files) {
        var r=new Request();r.setMode("NEW");r.setRootName("培训");r.setRequestKey(UUID.randomUUID().toString());
        var lib=new PmsKnowledgeLibrarySaveReqVO();lib.setName("培训");lib.setOpenStatus(false);r.setLibrary(lib);r.setFiles(Arrays.asList(files));return r;
    }
    @Test void rejectTraversalAndDepth() {
        for(String path:List.of("培训/../x.txt","/培训/x.txt","培训//x.txt","培训/C:/x.txt","培训/./x.txt"))
            assertThrows(ServiceException.class,()->PmsKnowledgeImportService.path(path,"培训",20));
        assertThrows(ServiceException.class,()->PmsKnowledgeImportService.path("培训/a/b.txt","培训",1));
    }
    @Test void preflightSkipsUnsupportedAndSameTitleWithoutWriting() {
        var result=service.prepare(request(entry("培训/a.pdf","a"),entry("培训/a.docx","b"),entry("培训/x.exe","x")),7L);
        assertEquals(1,result.stream().filter(i->i.getStatus().equals("PENDING")).count());
        verifyNoInteractions(libraryService,fileApi,documentService,folderService);
    }
    @Test void allSkippedDoesNotCreateEmptyLibrary() {
        assertThrows(ServiceException.class,()->service.create(request(entry("培训/x.exe","x")),7L));
        verifyNoInteractions(libraryService);
    }
    @Test void requestKeyRetryReturnsSameLibraryAndRejectsMutation() {
        var req=request(entry("培训/a.txt","a"));var a=service.create(req,7L);var b=service.create(req,7L);
        assertEquals(a.getId(),b.getId());verify(libraryService,times(1)).createLibrary(any(),eq(7L));
        req.getLibrary().setName("changed");assertThrows(ServiceException.class,()->service.create(req,7L));
    }
    @Test void ownerCannotReadOrFinishAnotherUsersTask() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);
        assertThrows(ServiceException.class,()->service.get(task.getId(),8L));
        assertThrows(ServiceException.class,()->service.finish(task.getId(),8L));
    }
    @Test void uploadResponseLostDoesNotCreateSecondDocument() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);var item=task.getItems().getFirst();
        var f=new MockMultipartFile("file","a.txt","text/plain",new byte[]{97});
        assertEquals(1,service.upload(task.getId(),item.getId(),f,7L).getSuccess());
        assertEquals(1,service.upload(task.getId(),item.getId(),f,7L).getSuccess());
        verify(documentService,times(1)).createDocument(any(),eq(7L));verify(fileApi,times(1)).createFileInfo(any(),any(),any(),any());
    }
    @Test void storedFileSurvivesRegistrationFailure() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);var item=task.getItems().getFirst();
        var f=new MockMultipartFile("file","a.txt","text/plain",new byte[]{97});
        when(documentService.createDocument(any(),eq(7L))).thenThrow(new IllegalStateException("fixture"));
        assertThrows(ServiceException.class,()->service.upload(task.getId(),item.getId(),f,7L));
        assertEquals(987L,items.selectById(item.getId()).getFileId());
        doReturn(12345L).when(documentService).createDocument(any(),eq(7L));
        assertEquals(1,service.upload(task.getId(),item.getId(),f,7L).getSuccess());
        verify(fileApi,times(1)).createFileInfo(any(),any(),any(),any());
    }
    @Test void resumeDoesNotAcceptChangedOrMissingFilesOrAddNewEntries() {
        var task=service.create(request(entry("培训/a.txt","a"),entry("培训/b.txt","b")),7L);
        var req=new Resume();req.setTaskId(task.getId());req.setFiles(List.of(entry("培训/a.txt","z"),entry("培训/new.txt","c")));
        var result=service.resume(req,7L);assertEquals(2,result.getFailed());assertEquals(2,result.getTotal());
        assertEquals("文件已变化",result.getItems().get(0).getReason());assertEquals("原文件未找到",result.getItems().get(1).getReason());
    }
    @Test void tamperedBytesNeverReachStorage() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);
        assertThrows(ServiceException.class,()->service.upload(task.getId(),task.getItems().getFirst().getId(),new MockMultipartFile("file",new byte[]{98}),7L));
        verifyNoInteractions(fileApi);
    }
    @Test void endedTaskRetainsRecordsButCannotResume() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);assertEquals("FINISHED",service.finish(task.getId(),7L).getStatus());
        var r=new Resume();r.setTaskId(task.getId());r.setFiles(List.of(entry("培训/a.txt","a")));
        assertThrows(ServiceException.class,()->service.resume(r,7L));assertEquals(1,service.get(task.getId(),7L).getTotal());
    }
    @Test void permissionRevocationStopsUpload() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);
        when(permissionApi.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(false);
        assertThrows(ServiceException.class,()->service.upload(task.getId(),task.getItems().getFirst().getId(),new MockMultipartFile("file",new byte[]{97}),7L));
        verifyNoInteractions(fileApi);
    }
    @Test void activeLeaseRejectsSecondTabAndExpiredLeaseRecovers() {
        var task=service.create(request(entry("培训/a.txt","a")),7L);
        var item=items.selectById(task.getItems().getFirst().getId());
        item.setStatus("UPLOADING");item.setLeaseToken("old");
        item.setLeaseUntil(java.time.LocalDateTime.now().plusMinutes(1));items.updateById(item);
        var f=new MockMultipartFile("file",new byte[]{97});
        assertThrows(ServiceException.class,()->service.upload(task.getId(),item.getId(),f,7L));
        verifyNoInteractions(fileApi);
        item.setLeaseUntil(java.time.LocalDateTime.now().minusMinutes(1));items.updateById(item);
        assertEquals(1,service.upload(task.getId(),item.getId(),f,7L).getSuccess());
    }
    @Test void existingImportKeepsSelectedRoot() {
        var req=request(entry("培训/a.txt","a"));req.setMode("EXISTING");
        req.setLibrary(null);req.setLibraryId(43210L);req.setFolderId(0L);
        doAnswer(invocation -> {
            var folder=new cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeFolderDO();
            folder.setLibraryId(43210L);folder.setParentId(0L);folder.setTitle("培训");folder.setStatus(1);folder.setPermissionId(1L);
            folderMapper.insert(folder);return folder.getId();
        }).when(folderService).createFolder(any(),eq(7L));
        var task=service.create(req,7L);
        service.upload(task.getId(),task.getItems().getFirst().getId(),new MockMultipartFile("file",new byte[]{97}),7L);
        verify(folderService).createFolder(argThat(r->r.getParentId()==0 && r.getTitle().equals("培训")),eq(7L));
        verify(documentService).createDocument(argThat(r->r.getFolderId()!=0),eq(7L));
        verifyNoInteractions(libraryService);
    }
    @Test void existingDuplicateSkipsWithoutOverwriting() {
        var folder=new cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeFolderDO();
        folder.setLibraryId(43210L);folder.setParentId(0L);folder.setTitle("培训");folder.setStatus(1);folder.setPermissionId(1L);folderMapper.insert(folder);
        var doc=new cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeDocumentDO();
        doc.setLibraryId(43210L);doc.setFolderId(folder.getId());doc.setParentId(0L);doc.setTitle("a");
        doc.setType(4);doc.setStatus(1);doc.setPermissionId(1L);doc.setContent("original");documentMapper.insert(doc);
        var req=request(entry("培训/a.txt","a"));req.setMode("EXISTING");req.setLibrary(null);req.setLibraryId(43210L);req.setFolderId(0L);
        assertEquals("SKIPPED",service.prepare(req,7L).getFirst().getStatus());
        assertEquals("original",documentMapper.selectById(doc.getId()).getContent());
        verifyNoInteractions(fileApi,documentService);
    }
    @Test void newModeRequiresCreatePermissionInAdditionToUpdate() {
        doReturn(false).when(permissionApi).hasAnyPermissions(7L,"pms:kb:library:create");
        assertThrows(ServiceException.class,()->service.create(request(entry("培训/a.txt","a")),7L));
        verifyNoInteractions(libraryService,fileApi);
    }
    @Test void cannotAttachAnotherTasksItem() {
        var a=service.create(request(entry("培训/a.txt","a")),7L);
        var b=service.create(request(entry("培训/b.txt","b")),7L);
        assertThrows(ServiceException.class,()->service.upload(a.getId(),b.getItems().getFirst().getId(),new MockMultipartFile("file",new byte[]{98}),7L));
        verifyNoInteractions(fileApi,documentService);
    }
}
