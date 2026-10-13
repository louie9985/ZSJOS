package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningSnapshotResourceService;
import cn.iocoder.yudao.module.zsjos.service.positioninginterview.PositioningInterviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentReviewStudentFilesTest {
    @Test void evidenceMustBelongToTheAuthorizedSubmission() {
        when(access.requireAccount(10L,40L,20L)).thenReturn(new MediaAccountDO().setId(40L));
        var card=new cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO();
        card.setSubmissionId(61L);card.setEvidence(List.of(new cn.iocoder.yudao.module.zsjos.service.positioning.PositioningEvidenceService.Evidence(99L,"凭证.png","image/png",3L,20L,null)));
        when(positioning.accountForReview(10L,40L,20L)).thenReturn(List.of(card));
        var info=new FileInfoRespDTO();info.setType("image/png");info.setSize(3L);when(files.getFileInfo(99L)).thenReturn(info);
        assertNull(service.file(10L,40L,"evidence",61L,99L,20L).url());
        assertThrows(RuntimeException.class,()->service.file(10L,40L,"evidence",62L,99L,20L));
        assertThrows(RuntimeException.class,()->service.file(10L,40L,"evidence",61L,98L,20L));
    }
    @InjectMocks ContentReviewStudentService service;
    @Mock ContentReviewStudentAccess access;
    @Mock MediaAccountProfileService profiles;
    @Mock PositioningCardService positioning;
    @Mock PositioningSnapshotResourceService resources;
    @Mock PositioningInterviewService interviews;
    @Mock FileApi files;
    @Test void fileMustBeAnAuthorizedProfileAttachmentAndNoStorageUrlIsReturned() throws Exception {
        when(access.requireAccount(10L,40L,20L)).thenReturn(new MediaAccountDO().setId(40L));
        var profile=new MediaAccountProfileVO();var file=new MediaAccountProfileVO.FileVO();file.setId(99L);file.setName("档案.pdf");
        profile.setFiles(Map.of("attachment",file));when(profiles.getForReview(10L,40L,20L)).thenReturn(profile);
        assertThrows(RuntimeException.class,()->service.file(10L,40L,"profile",null,98L,20L));
        verifyNoInteractions(files);
        var info=new FileInfoRespDTO();info.setType("application/pdf");info.setSize(3L);when(files.getFileInfo(99L)).thenReturn(info);
        when(files.getFileContent(99L)).thenReturn(new byte[]{1,2,3});
        assertNull(service.file(10L,40L,"profile",null,99L,20L).url());
        assertArrayEquals(new byte[]{1,2,3},service.fileContent(10L,40L,"profile",null,99L,20L));
        verify(files,never()).presignGetUrl(anyLong(),anyInt());
        doThrow(new IllegalArgumentException("expired")).when(access).requireAccount(10L,40L,20L);
        assertThrows(IllegalArgumentException.class,()->service.fileContent(10L,40L,"profile",null,99L,20L));
        verify(files,times(1)).getFileContent(99L);
    }
}
