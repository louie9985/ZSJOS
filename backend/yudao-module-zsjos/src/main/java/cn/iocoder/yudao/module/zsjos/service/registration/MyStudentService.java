package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;

public interface MyStudentService {
    PageResult<MyStudentRespVO> getMyPage(Long userId, MyStudentPageReqVO reqVO);
    PageResult<MyStudentRespVO> getMediaPage(Long userId, MyStudentPageReqVO reqVO);
    PageResult<MyStudentRespVO> getDirectorPage(Long userId, MyStudentPageReqVO reqVO);
    MyStudentRespVO getMyStudent(Long userId, Long personId);
    MyStudentRespVO getMediaStudent(Long userId, Long personId);
    MyStudentRespVO getDirectorStudent(Long userId, Long personId);
    MyStudentRespVO getMyStudentByService(Long userId, Long relationId);
}
