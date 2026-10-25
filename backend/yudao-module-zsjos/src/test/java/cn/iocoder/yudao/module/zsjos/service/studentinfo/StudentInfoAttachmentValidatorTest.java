package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentInfoAttachmentValidatorTest {
    @Test void acceptsStoredDocxEntriesWithDataDescriptors() throws Exception {
        // Streaming ZIP writer: STORED entries, bit 3 set, sizes/CRC in data descriptors.
        byte[] docx = Base64.getDecoder().decode("UEsDBBQACAAAANWCVV0AAAAAAAAAAAAAAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbDxUeXBlcy8+UEsHCMccFzwIAAAACAAAAFBLAwQUAAgAAADVglVdAAAAAAAAAAAAAAAAEQAAAHdvcmQvZG9jdW1lbnQueG1sPGRvY3VtZW50Lz5QSwcIX1vRTAsAAAALAAAAUEsBAhQAFAAIAAAA1YJVXcccFzwIAAAACAAAABMAAAAAAAAAAAAAAIABAAAAAFtDb250ZW50X1R5cGVzXS54bWxQSwECFAAUAAgAAADVglVdX1vRTAsAAAALAAAAEQAAAAAAAAAAAAAAgAFJAAAAd29yZC9kb2N1bWVudC54bWxQSwUGAAAAAAIAAgCAAAAAkwAAAAAA");
        try (var stream = new ZipInputStream(new ByteArrayInputStream(docx))) {
            assertThrows(ZipException.class, stream::getNextEntry);
        }
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                StudentInfoAttachmentValidator.validate(docx, "报名申请表.docx", StudentInfoFields.extensions("application_images")));
        assertThrows(Exception.class, () -> StudentInfoAttachmentValidator.validate(docx, "test.docx", StudentInfoFields.extensions("work_proof")));
    }
    @Test void rejectsTruncatedAndNonWordZipsWithActionableErrors() throws Exception {
        var broken = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> StudentInfoAttachmentValidator.validate(new byte[]{80, 75, 3, 4}, "test.docx", List.of("docx")));
        assertTrue(broken.getMessage().contains("DOCX"));
        var buffer = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("unrelated.txt")); zip.write("text".getBytes()); zip.closeEntry();
        }
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> StudentInfoAttachmentValidator.validate(buffer.toByteArray(), "test.docx", List.of("docx")));
    }
    @Test void fieldSpecificFormatsRejectDisguisedAndUnsupportedFiles() throws Exception {
        byte[] pdf="%PDF-1.7\nsynthetic\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("application/pdf",StudentInfoAttachmentValidator.validate(pdf,"test.pdf",StudentInfoFields.extensions("education_report")));
        assertThrows(Exception.class,()->StudentInfoAttachmentValidator.validate(pdf,"test.png",StudentInfoFields.extensions("portrait")));
        assertThrows(Exception.class,()->StudentInfoAttachmentValidator.validate(pdf,"test.pdf",StudentInfoFields.extensions("identity_images")));
        assertThrows(Exception.class,()->StudentInfoAttachmentValidator.validate("fake".getBytes(),"test.pdf",List.of("pdf")));
        var buffer=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(buffer)) { for(String entry:List.of("[Content_Types].xml","word/document.xml")) {zip.putNextEntry(new ZipEntry(entry));zip.write("<xml/>".getBytes());zip.closeEntry();} }
        assertTrue(StudentInfoAttachmentValidator.validate(buffer.toByteArray(),"test.docx",StudentInfoFields.extensions("application_images")).contains("wordprocessingml"));
        assertThrows(Exception.class,()->StudentInfoAttachmentValidator.validate(buffer.toByteArray(),"test.docx",StudentInfoFields.extensions("work_proof")));
    }
    @Test void orderedShippingAddressesAndTemplateUpgradeKeepSnapshotBoundary() {
        var fields=new StudentInfoFields();var presets=fields.presets();
        int gift=java.util.stream.IntStream.range(0,presets.size()).filter(i->presets.get(i).getKey().equals("gift_shipping_address")).findFirst().orElseThrow();
        assertEquals("certificate_shipping_address",presets.get(gift+1).getKey());assertTrue(presets.get(gift).getSensitive());
        var old=presets.stream().filter(f->!f.getKey().endsWith("shipping_address")).toList();
        String snapshot=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(old);
        assertEquals(32,fields.upgradeConfig(fields.parse(snapshot)).size());assertEquals(30,fields.parse(snapshot).size());
        assertEquals(List.of("pdf"),presets.stream().filter(f->f.getKey().equals("education_report")).findFirst().orElseThrow().getAllowedExtensions());
    }
}
