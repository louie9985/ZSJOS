package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;

public interface MyStudentService {
    PageResult<MyStudentRespVO> getMyPage(Long userId, MyStudentPageReqVO reqVO);
    MyStudentRespVO getMyStudent(Long userId, Long personId);
}
