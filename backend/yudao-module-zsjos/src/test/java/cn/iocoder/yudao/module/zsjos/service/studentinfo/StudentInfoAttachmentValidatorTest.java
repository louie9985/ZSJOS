package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentInfoAttachmentValidatorTest {
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
