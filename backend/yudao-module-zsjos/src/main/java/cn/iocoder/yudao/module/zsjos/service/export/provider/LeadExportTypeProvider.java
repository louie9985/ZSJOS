package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeadExportTypeProvider extends AbstractPagedExportTypeProvider<LeadManagementPageReqVO, LeadManagementRespVO> {

    @Resource
    private LeadManagementService leadManagementService;

    @Override public String getType() { return "lead"; }
    @Override public String getCreatePermission() { return "zsjos:export:lead"; }
    @Override protected Class<LeadManagementPageReqVO> requestType() { return LeadManagementPageReqVO.class; }
    @Override protected PageResult<LeadManagementRespVO> getPage(LeadManagementPageReqVO request, Long creatorUserId) {
        return leadManagementService.getLeadPage(request, creatorUserId);
    }
    @Override protected List<String> columns() {
        return List.of("客资编号", "姓名", "手机号", "微信号", "来源渠道", "客资分类", "客资状态", "分配状态",
                "负责人", "提交时间", "意向产品");
    }
    @Override protected List<Object> toRow(LeadManagementRespVO item) {
        return List.of(value(item.getLeadNo()), value(item.getSubmittedName()), value(item.getSubmittedMobile()),
                value(item.getSubmittedWechatId()), value(item.getSourceChannel()), value(item.getLeadCategory()),
                value(item.getStatus()), value(item.getAssignmentStatus()), value(item.getOwnerUserName()),
                value(item.getSubmittedAt()), item.getIntendedProducts() == null ? "" : item.getIntendedProducts().stream()
                        .map(product -> product.getSpuName() == null ? "" : product.getSpuName())
                        .filter(name -> !name.isBlank()).distinct()
                        .reduce((left, right) -> left + "、" + right).orElse(""));
    }
    @Override protected String sheetName() { return "客资"; }

    private static Object value(Object value) {
        return value == null ? "" : value instanceof Long ? value.toString() : value;
    }
}
