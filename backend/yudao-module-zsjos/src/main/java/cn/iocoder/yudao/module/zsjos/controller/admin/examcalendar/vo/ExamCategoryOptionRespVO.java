package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;

import java.util.List;

public record ExamCategoryOptionRespVO(Long id, String name,
                                       List<ZsjosProductCategoryPathNodeVO> path) {
}
