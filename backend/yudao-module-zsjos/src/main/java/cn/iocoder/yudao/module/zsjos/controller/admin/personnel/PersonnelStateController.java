package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PersonnelStateService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/personnel")
public class PersonnelStateController {
    @Resource private PersonnelStateService service;

    @GetMapping("/{userId}/state")
    @PreAuthorize("@ss.hasPermission('zsjos:personnel:query')")
    public CommonResult<PersonnelStateRespVO> get(@PathVariable Long userId) { return success(service.get(userId)); }

    @PutMapping("/{userId}/state")
    @PreAuthorize("@ss.hasPermission('zsjos:personnel:update-state')")
    public CommonResult<Boolean> update(@PathVariable Long userId, @Valid @RequestBody PersonnelStateUpdateReqVO reqVO) {
        service.update(userId, reqVO, getLoginUserId()); return success(true);
    }
}
