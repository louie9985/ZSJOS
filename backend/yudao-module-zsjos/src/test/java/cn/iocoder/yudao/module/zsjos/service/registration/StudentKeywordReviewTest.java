package cn.iocoder.yudao.module.zsjos.service.registration;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentKeywordReviewTest {
 @Test void sortingDoesNotRestartCandidateScanForEveryResultPage(){
  var service=mock(MyStudentServiceImpl.class,CALLS_REAL_METHODS);
  doAnswer(call->{MyStudentPageReqVO q=call.getArgument(1);assertEquals("needle",q.getProjectionKeyword());assertNull(q.getSortField());
   List<MyStudentRespVO> rows=new ArrayList<>();int start=(q.getPageNo()-1)*200;
   for(int i=start;i<Math.min(start+200,201);i++){var r=new MyStudentRespVO();r.setPersonId((long)i+1);r.setName(i==200?"needle-A":"needle-Z");rows.add(r);}return new PageResult<>(rows,201L);
  }).when(service).getMyPage(eq(1L),argThat(q->q!=null&&q.getKeyword()==null));
  var request=new MyStudentPageReqVO();request.setKeyword("needle");request.setSortField("name");request.setSortOrder("ascend");request.setPageNo(1);request.setPageSize(10);
  var page=service.getMyPage(1L,request);assertEquals(201L,page.getTotal());assertEquals("needle-A",page.getList().getFirst().getName());
  verify(service,times(2)).getMyPage(eq(1L),argThat(q->q!=null&&q.getKeyword()==null));
 }
}
