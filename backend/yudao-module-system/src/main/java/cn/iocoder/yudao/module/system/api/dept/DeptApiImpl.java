package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

/**
 * 部门 API 实现类
 *
 * @author 芋道源码
 */
@Service
public class DeptApiImpl implements DeptApi {

    @Resource
    private DeptService deptService;

    @Override
    @DataPermission(enable = false) // 精确部门基础资料查询不继承调用方数据范围，租户与逻辑删除仍由 System DAL 保证
    public DeptRespDTO getDept(Long id) {
        DeptDO dept = deptService.getDept(id);
        return BeanUtils.toBean(dept, DeptRespDTO.class);
    }

    @Override
    @DataPermission(enable = false) // 指定 ID 的跨模块资料拼接需要完整返回，不能被当前账号的数据范围裁剪
    public List<DeptRespDTO> getDeptList(Collection<Long> ids) {
        List<DeptDO> depts = deptService.getDeptList(ids);
        return BeanUtils.toBean(depts, DeptRespDTO.class);
    }

    @Override
    public void validateDeptList(Collection<Long> ids) {
        deptService.validateDeptList(ids);
    }

    @Override
    public List<DeptRespDTO> getChildDeptList(Collection<Long> ids) {
        List<DeptDO> childDeptList = deptService.getChildDeptList(ids);
        return BeanUtils.toBean(childDeptList, DeptRespDTO.class);
    }

    @Override
    @DataPermission(enable = false) // 负责人关系是跨模块业务资格判断的基础资料，不是调用方的业务列表范围
    public List<DeptRespDTO> getDeptListByLeaderUserId(Long leaderUserId) {
        return BeanUtils.toBean(deptService.getDeptListByLeaderUserId(leaderUserId), DeptRespDTO.class);
    }

    @Override
    public List<DeptRespDTO> getParentDeptList(Long id) {
        return BeanUtils.toBean(deptService.getParentDeptList(id), DeptRespDTO.class);
    }

}
