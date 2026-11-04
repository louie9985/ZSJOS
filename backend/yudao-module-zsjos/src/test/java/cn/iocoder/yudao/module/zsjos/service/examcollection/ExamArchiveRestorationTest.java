package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamArchiveRestoration.*;

class ExamArchiveRestorationTest {
    Snapshot snapshot(int version, Map<String,String> values, Attachment... attachments) {
        var fields = new ArrayList<Field>(); var answers = new ArrayList<StudentInfoFormValueDO>();
        values.forEach((key,value)->{var f=new Field();f.setKey(key);f.setLabel(key);f.setType("text");fields.add(f);
            var v=new StudentInfoFormValueDO();v.setFieldKey(key);v.setValueText(value);answers.add(v);});
        return new Snapshot(1L,2L,version,fields,answers,List.of(attachments));
    }
    Map<String,String> values(Result result) {
        var values=new HashMap<String,String>();result.snapshot().answers().forEach(v->values.put(v.getFieldKey(),v.getValueText()));return values;
    }
    @Test void restoresWrongRepeatedSubmissionsButKeepsLaterUnrelatedStaffEdit() {
        var before=snapshot(0,Map.of("name","original","mobile","old","education","old"));
        var first=snapshot(1,Map.of("name","wrong1","mobile","wrong1","education","old"));
        var staff=snapshot(2,Map.of("name","wrong1","mobile","wrong1","education","new"));
        var repeat=snapshot(3,Map.of("name","wrong2","mobile","wrong2","education","new"));
        var later=snapshot(4,Map.of("name","staff corrected","mobile","wrong2","education","new"));
        var result=restore(before,List.of(new Revision(first,true,Set.of("name","mobile")),new Revision(staff,false,Set.of()),
                new Revision(repeat,true,Set.of("name","mobile")),new Revision(later,false,Set.of())));
        assertEquals(Map.of("name","staff corrected","mobile","old","education","new"),values(result));
        assertEquals(Set.of("mobile"),result.restoredKeys());assertEquals(Set.of("name"),result.retainedLaterKeys());
        assertEquals("wrong2",repeat.answers().stream().filter(v->v.getFieldKey().equals("name")).findFirst().orElseThrow().getValueText());
    }
    @Test void explicitLaterWriteOfSameValueIsRetained() {
        var before=snapshot(0,Map.of("name","original"));var wrong=snapshot(1,Map.of("name","same"));var later=snapshot(2,Map.of("name","same"));
        var result=restore(before,List.of(new Revision(wrong,true,Set.of("name")),new Revision(later,false,Set.of("name"))));
        assertEquals("same",values(result).get("name"));assertTrue(result.restoredKeys().isEmpty());
    }
    @Test void restoresCrossSourceFilesAndRemovesFieldsIntroducedOnlyByWrongSubmission() {
        var original=new Attachment("registration",1L,"photo","","same.png","image/png",1L);
        var wrongFile=new Attachment("exam",1L,"photo","","same.png","image/png",1L);
        var before=snapshot(0,Map.of("name","original"),original);
        var wrong=snapshot(1,Map.of("name","wrong","wechat","wrong"),wrongFile);
        var result=restore(before,List.of(new Revision(wrong,true,Set.of("name","photo","wechat"))));
        assertEquals(List.of(original),result.snapshot().attachments());assertFalse(values(result).containsKey("wechat"));
        assertEquals(Set.of("name","photo","wechat"),result.restoredKeys());
    }
    @Test void laterClearAndNewFileAreNotOverwritten() {
        var wrongFile=new Attachment("exam",1L,"photo","","wrong.png","image/png",1L);
        var newFile=new Attachment("service",2L,"photo","","new.png","image/png",1L);
        var result=restore(snapshot(0,Map.of("name","original")),List.of(
                new Revision(snapshot(1,Map.of("name","wrong"),wrongFile),true,Set.of("name","photo")),
                new Revision(snapshot(2,Map.of(),newFile),false,Set.of())));
        assertFalse(values(result).containsKey("name"));assertEquals(List.of(newFile),result.snapshot().attachments());
    }
    @Test void refusesMissingRevisionsInsteadOfGuessingRestoration() {
        assertThrows(IllegalArgumentException.class,()->restore(snapshot(0,Map.of()),List.of(new Revision(snapshot(2,Map.of()),true,Set.of()))));
        var foreign=new Snapshot(3L,2L,1,List.of(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->restore(snapshot(0,Map.of()),List.of(new Revision(foreign,true,Set.of()))));
    }
}
