package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamCollectionExportTest {
    private ExamCollectionExportService service;
    private ExamCollectionTaskMapper tasks;
    private ExamCollectionPermissionProvider permissions;
    private StudentServiceArchiveStore archives;
    private StudentServiceArchiveFacade files;
    private ExamCollectionSubmissionMapper submissions;
    private ExamCollectionQueryService queries;
    private ExamCollectionTaskDO task;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        service = new ExamCollectionExportService(); tasks = mock(ExamCollectionTaskMapper.class);
        permissions = mock(ExamCollectionPermissionProvider.class); archives = mock(StudentServiceArchiveStore.class);
        files = mock(StudentServiceArchiveFacade.class);
        var members = mock(ExamCollectionMemberMapper.class); submissions = mock(ExamCollectionSubmissionMapper.class);
        queries = mock(ExamCollectionQueryService.class); ReflectionTestUtils.setField(service,"queries",queries);
        ReflectionTestUtils.setField(service,"tasks",tasks); ReflectionTestUtils.setField(service,"permissions",permissions);
        ReflectionTestUtils.setField(service,"members",members); ReflectionTestUtils.setField(service,"submissions",submissions);
        ReflectionTestUtils.setField(service,"archives",archives); ReflectionTestUtils.setField(service,"archiveFiles",files);
        task = new ExamCollectionTaskDO(); task.setId(1L); task.setTitle("中国营养学会2026年12月公共营养师考试报名核实表"); task.setRevision(0);
        var field = new Field(); field.setKey("portrait"); field.setLabel("证件照"); field.setType("images");
        var nameField = new Field(); nameField.setKey("name"); nameField.setLabel("姓名"); nameField.setType("text");
        var mobileField = new Field(); mobileField.setKey("mobile"); mobileField.setLabel("手机号"); mobileField.setType("text");
        var identityField = new Field(); identityField.setKey("id_card"); identityField.setLabel("身份证号"); identityField.setType("text");
        task.setFieldsSnapshot(JsonUtils.toJsonString(List.of(nameField, mobileField, identityField, field)));
        when(tasks.selectById(1L)).thenReturn(task); when(tasks.lock(1L)).thenReturn(task);
        var member = new ExamCollectionMemberDO(); member.setServiceRelationId(10L); member.setClassId(5L); member.setClassNameSnapshot("合成班级");
        when(members.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(member));
        when(submissions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        snapshot(0, imageBytes().length);
    }
    private byte[] imageBytes() {
        try (var output = new java.io.ByteArrayOutputStream()) {
            var image = new java.awt.image.BufferedImage(20, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private void snapshot(int revision, long size) {
        var name = new StudentInfoFormValueDO(); name.setFieldKey("name"); name.setValueText("合成学员");
        when(archives.current(10L)).thenReturn(new StudentServiceArchiveStore.Snapshot(10L,20L,revision,List.of(),List.of(name),
                List.of(new StudentServiceArchiveStore.Attachment("registration",30L,"portrait","","IMG.jpg","image/jpeg",size))));
    }
    @Test void zipNameUsesTaskTitleAndBeijingExportMinute() {
        String minute = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        assertEquals(task.getTitle() + "(" + minute + ").zip", service.downloadName(1L));
    }

    @Test void orderedTokensSafeNamesAndCaseInsensitiveCollisions() {
        assertEquals("合成_固定",ExamCollectionExportNaming.name(List.of("name","text:固定"),Map.of("name","合成")));
        assertEquals("_CON",ExamCollectionExportNaming.safe("CON"));
        assertFalse(ExamCollectionExportNaming.safe("../../a\\b\n").contains("/"));
        var used = new HashSet<String>();
        assertEquals("A.jpg",ExamCollectionExportNaming.unique("","A",".jpg",used));
        assertEquals("a_2.jpg",ExamCollectionExportNaming.unique("","a",".jpg",used));
        assertThrows(ServiceException.class,()->ExamCollectionExportNaming.extension("text/html"));
        var settings = new Settings(); settings.setNaming(List.of("unknown"));
        assertThrows(ServiceException.class,()->ExamCollectionExportNaming.validate(settings,Set.of("portrait")));
        settings.setNaming(List.of("text:"));
        assertThrows(ServiceException.class,()->ExamCollectionExportNaming.validate(settings,Set.of("portrait")));
    }
    @Test void defaultsOldTaskSaveAndOptimisticConflict() {
        assertEquals(0,service.settings(1L).getRevision());
        var settings = service.save(1L,new Settings()); assertEquals(1,settings.getRevision());
        task.setExportSettingsJson(JsonUtils.toJsonString(settings));
        assertThrows(ServiceException.class,()->service.save(1L,new Settings()));
        verify(tasks,times(1)).updateById(any(ExamCollectionTaskDO.class));
    }
    @Test void previewAndZipUseTheSamePathsAndOriginalBytes() throws Exception {
        var request = new Request(); request.getSettings().setGroupByClass(true);
        var preview = service.preview(1L,request);
        assertEquals(1,preview.people()); assertEquals(1,preview.files()); assertEquals(imageBytes().length,preview.bytes());
        assertTrue(preview.paths().getFirst().startsWith("合成班级/证件照/合成学员_证件照_"));
        when(files.read(10L,30L,"registration")).thenReturn(imageBytes());
        request.setFingerprint(preview.fingerprint()); Path output = service.download(1L,request);
        try (var zip = new ZipFile(output.toFile())) {
            assertArrayEquals(imageBytes(),zip.getInputStream(zip.getEntry(preview.paths().getFirst())).readAllBytes());
            assertNotNull(zip.getEntry("导出说明.txt"));
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("(" + task.getTitle() + ")表格视图.xlsx")))) {
                var sheet = book.getSheetAt(0);
                assertEquals(4, sheet.getRow(0).getLastCellNum());
                assertEquals("姓名", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("合成学员", sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals(1, book.getAllPictures().size());
                var drawing = sheet.getDrawingPatriarch();
                assertEquals(1, drawing.getShapes().size());
                var picture = (org.apache.poi.xssf.usermodel.XSSFPicture) drawing.getShapes().getFirst();
                assertEquals(3, picture.getClientAnchor().getCol1());
                assertEquals(1, picture.getClientAnchor().getRow1());
            }
        } finally { Files.delete(output); }
    }
    @Test void informationOnlyAndLongIdentifiersRemainText() throws Exception {
        var identity = new StudentInfoFormValueDO(); identity.setFieldKey("id_card"); identity.setValueText("012345678901234567");
        when(archives.current(10L)).thenReturn(new StudentServiceArchiveStore.Snapshot(10L,20L,1,List.of(),List.of(identity),List.of()));
        var request = new Request();
        request.setFingerprint(service.preview(1L, request).fingerprint());
        Path output = service.download(1L, request);
        try (var zip = new ZipFile(output.toFile());
             var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("(" + task.getTitle() + ")表格视图.xlsx")))) {
            var cell = book.getSheetAt(0).getRow(1).getCell(2);
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, cell.getCellType());
            assertEquals("012345678901234567", cell.getStringCellValue());
        } finally { Files.delete(output); }
        verifyNoInteractions(files);
    }

    @Test void multipleAttachmentsHaveSeparateCellsAndDocumentReferences() throws Exception {
        var name = new StudentInfoFormValueDO(); name.setFieldKey("name"); name.setValueText("合成学员");
        var attachments = List.of(
                new StudentServiceArchiveStore.Attachment("registration",30L,"portrait","front","photo.png","image/png",(long) imageBytes().length),
                new StudentServiceArchiveStore.Attachment("registration",31L,"portrait","back","document.pdf","application/pdf",2L));
        when(archives.current(10L)).thenReturn(new StudentServiceArchiveStore.Snapshot(10L,20L,1,List.of(),List.of(name),attachments));
        when(files.read(10L,30L,"registration")).thenReturn(imageBytes());
        when(files.read(10L,31L,"registration")).thenReturn(new byte[]{4,5});
        var request = new Request();
        var preview = service.preview(1L, request); request.setFingerprint(preview.fingerprint());
        Path output = service.download(1L, request);
        try (var zip = new ZipFile(output.toFile());
             var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("(" + task.getTitle() + ")表格视图.xlsx")))) {
            var sheet = book.getSheetAt(0);
            assertEquals("证件照（1）", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("证件照（2）", sheet.getRow(0).getCell(4).getStringCellValue());
            assertEquals(1, book.getAllPictures().size());
            assertNotNull(sheet.getRow(1).getCell(4).getHyperlink());
            String address = sheet.getRow(1).getCell(4).getHyperlink().getAddress();
            assertNotNull(zip.getEntry(java.net.URLDecoder.decode(address, java.nio.charset.StandardCharsets.UTF_8)));
        } finally { Files.delete(output); }
    }

    @Test void changedArchiveRequiresPreviewAndBoundsFailBeforeReadingBytes() {
        var request = new Request(); request.setFingerprint(service.preview(1L,request).fingerprint());
        snapshot(1,3L); assertThrows(ServiceException.class,()->service.download(1L,request));
        snapshot(1,ExamCollectionExportService.MAX_BYTES+1);
        assertThrows(ServiceException.class,()->service.preview(1L,request)); verifyNoInteractions(files);
    }
    @Test void foreignSelectionAndUnauthorizedTaskCannotReadMaterials() {
        var request = new Request(); request.setServiceIds(List.of(99L));
        assertThrows(ServiceException.class,()->service.preview(1L,request)); verifyNoInteractions(archives,files);
        doThrow(new ServiceException(403,"denied")).when(permissions).check(eq(1L),eq("query"),any());
        assertThrows(ServiceException.class,()->service.preview(1L,new Request())); verifyNoInteractions(archives,files);
    }
    @Test void filteredEmptyDoesNotFetchBytes() {
        var request = new Request(); request.setClassIds(List.of(99L));
        assertEquals(0,service.preview(1L,request).files()); verifyNoInteractions(archives,files);
    }
    @Test @SuppressWarnings("unchecked") void rawUnmatchedSubmissionRemainsSeparateAndReadsThroughBoundEndpoint() throws Exception {
        var row = new ExamCollectionSubmissionDO(); row.setId(50L); row.setSequenceNo(3L); row.setRevision(1); row.setStatus("EXCEPTION");
        when(submissions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));
        var detail = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.SubmissionDetail();
        detail.setValues(Map.of("name","合成异常学员"));
        var file = new cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Image();
        file.setId(60L); file.setFieldKey("portrait"); file.setName("source.png"); file.setContentType("application/pdf"); file.setSize(2L);
        detail.setAttachments(List.of(file)); when(queries.submission(1L,50L)).thenReturn(detail);
        var request = new Request(); request.setMode(Mode.SUBMISSIONS);
        request.getSettings().setNaming(List.of("submittedAt"));
        row.setSubmittedAt(java.time.LocalDateTime.of(2026, 11, 8, 14, 51, 55));
        assertEquals(0,service.preview(1L,request).files());
        request.setIncludeAdditional(true);
        var preview = service.preview(1L,request); assertEquals(1,preview.people());
        assertTrue(preview.paths().getFirst().contains("第3次提交_待处理异常"));
        assertTrue(preview.paths().getFirst().endsWith("2026_11_08 14_51_55.pdf"));
        request.setFingerprint(preview.fingerprint()); when(queries.attachment(1L,50L,60L)).thenReturn(new byte[]{4,5});
        Path output = service.download(1L,request);
        try (var zip = new ZipFile(output.toFile())) {
            assertArrayEquals(new byte[]{4,5},zip.getInputStream(zip.getEntry(preview.paths().getFirst())).readAllBytes());
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("(" + task.getTitle() + ")表格视图.xlsx")))) {
                var cell = book.getSheetAt(0).getRow(1).getCell(3);
                assertNotNull(cell.getHyperlink());
                assertEquals(org.springframework.web.util.UriUtils.encodePath(preview.paths().getFirst(), java.nio.charset.StandardCharsets.UTF_8), cell.getHyperlink().getAddress());
                assertEquals(0, book.getAllPictures().size());
            }
        }
        finally { Files.delete(output); }
        verifyNoInteractions(files,archives);
    }
    @Test @SuppressWarnings("unchecked") void submissionTimeNamingUsesRecordedTimeAndMissingMarker() {
        var request = new Request();
        request.getSettings().setLayout(Layout.FLAT);
        request.getSettings().setNaming(List.of("submittedAt"));
        assertEquals(List.of("未记录.jpg"), service.preview(1L, request).paths());
        var row = new ExamCollectionSubmissionDO();
        row.setId(50L); row.setServiceRelationId(10L); row.setSequenceNo(1L); row.setStatus("ARCHIVED");
        row.setSubmittedAt(java.time.LocalDateTime.of(2026, 11, 8, 14, 51, 55));
        when(submissions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));
        assertEquals(List.of("2026_11_08 14_51_55.jpg"), service.preview(1L, request).paths());
        request.getSettings().setOverrides(Map.of("portrait", List.of("name", "submittedAt")));
        assertEquals(List.of("合成学员_2026_11_08 14_51_55.jpg"), service.preview(1L, request).paths());
    }
    @Test void selectedMaterialNamingAndFlatLayoutOverrideDefault() {
        var request = new Request(); request.getSettings().setLayout(Layout.FLAT);
        request.getSettings().setOverrides(Map.of("portrait",List.of("text:考生","name")));
        assertEquals(List.of("考生_合成学员.jpg"),service.preview(1L,request).paths());
        request.setFieldKeys(List.of("foreign")); assertThrows(ServiceException.class,()->service.preview(1L,request));
    }
}
