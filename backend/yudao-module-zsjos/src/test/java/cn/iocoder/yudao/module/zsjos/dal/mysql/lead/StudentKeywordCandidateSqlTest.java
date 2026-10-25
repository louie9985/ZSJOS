package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StudentKeywordCandidateSqlTest {
 @Test void literalKeywordPrefilterCannotTreatUserMetacharactersAsLikeWildcards(){
  var mapper=mock(PersonMapper.class,CALLS_REAL_METHODS);doReturn(PageResult.empty()).when(mapper).selectPage(any(MyStudentPageReqVO.class),any(QueryWrapperX.class));
  var request=new MyStudentPageReqVO();request.setProjectionKeyword("literal%_\\suffix");mapper.selectMediaStudentPage(request,1L,null);
  var wrapper=ArgumentCaptor.forClass(QueryWrapperX.class);verify(mapper).selectPage(eq(request),wrapper.capture());
  String sql=wrapper.getValue().getSqlSegment();assertTrue(sql.contains("LOCATE("));assertTrue(sql.contains("sk.student_name_snapshot"));assertFalse(sql.contains(" LIKE "));
  assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(request.getProjectionKeyword()));assertTrue(sql.contains("tenant_id=zsjos_person.tenant_id"));
 }

 @Test void candidatePrefilterIncludesOrderNumberForAuthorizedAggregateProjection(){
  var mapper=mock(PersonMapper.class,CALLS_REAL_METHODS);doReturn(PageResult.empty()).when(mapper).selectPage(any(MyStudentPageReqVO.class),any(QueryWrapperX.class));
  var request=new MyStudentPageReqVO();request.setProjectionKeyword("SO-2026");mapper.selectMediaStudentPage(request,1L,null);
  var wrapper=ArgumentCaptor.forClass(QueryWrapperX.class);verify(mapper).selectPage(eq(request),wrapper.capture());
  String sql=wrapper.getValue().getSqlSegment();assertTrue(sql.contains("zsjos_order so"));assertTrue(sql.contains("sr.order_id=so.id"));
  assertTrue(sql.contains("so.order_no"));assertTrue(sql.contains("sr.person_id=zsjos_person.id"));
 }
}
