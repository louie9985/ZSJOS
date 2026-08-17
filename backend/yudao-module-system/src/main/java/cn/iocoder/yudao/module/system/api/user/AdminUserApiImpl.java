package cn.iocoder.yudao.module.system.api.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCreateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserOrganizationUpdateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserPartnerConversionReqDTO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * Admin 用户 API 实现类
 *
 * @author 芋道源码
 */
@Service
public class AdminUserApiImpl implements AdminUserApi {

    @Override
    public Long createUser(AdminUserCreateReqDTO reqDTO) {
        return userService.createUser(BeanUtils.toBean(reqDTO, UserSaveReqVO.class));
    }

    @Override
    public void updateUserOrganization(AdminUserOrganizationUpdateReqDTO reqDTO) {
        AdminUserDO current = userService.getUser(reqDTO.getUserId());
        UserSaveReqVO reqVO = BeanUtils.toBean(current, UserSaveReqVO.class);
        reqVO.setId(reqDTO.getUserId());
        reqVO.setDeptId(reqDTO.getDeptId());
        reqVO.setPostIds(reqDTO.getPostIds());
        userService.updateUser(reqVO);
    }

    @Resource
    private AdminUserService userService;
    @Resource
    private DeptService deptService;

    @Override
    @DataPermission(enable = false) // 忽略数据权限，避免因为过滤，导致无法查询用户。类似：https://github.com/YunaiV/ruoyi-vue-pro/issues/1051
    public AdminUserRespDTO getUser(Long id) {
        AdminUserDO user = userService.getUser(id);
        return BeanUtils.toBean(user, AdminUserRespDTO.class);
    }

    @Override
    public Long convertPartnerToEmployee(AdminUserPartnerConversionReqDTO reqDTO) {
        if (reqDTO.getExistingUserId() == null || userService.getUser(reqDTO.getExistingUserId()) == null) {
            return createUser(new AdminUserCreateReqDTO().setUsername(reqDTO.getUsername())
                    .setPassword(reqDTO.getPassword()).setNickname(reqDTO.getNickname()).setMobile(reqDTO.getMobile())
                    .setDeptId(reqDTO.getDeptId()).setPostIds(reqDTO.getPostIds()));
        }
        AdminUserDO current = userService.getUser(reqDTO.getExistingUserId());
        UserSaveReqVO update = BeanUtils.toBean(current, UserSaveReqVO.class).setId(current.getId())
                .setUsername(reqDTO.getUsername()).setNickname(reqDTO.getNickname()).setMobile(reqDTO.getMobile())
                .setDeptId(reqDTO.getDeptId()).setPostIds(reqDTO.getPostIds());
        userService.updateUser(update);
        userService.updateUserPassword(current.getId(), reqDTO.getPassword());
        userService.updateUserStatus(current.getId(), cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus());
        return current.getId();
    }

    @Override
    @DataPermission(enable = false) // 指定手机号的跨模块查询用于数据拼接，不应受当前用户数据范围影响
    public AdminUserRespDTO getUserByMobile(String mobile) {
        return BeanUtils.toBean(userService.getUserByMobile(mobile), AdminUserRespDTO.class);
    }

    @Override
    public List<AdminUserRespDTO> getUserListBySubordinate(Long id) {
        // 1.1 获取用户负责的部门
        List<DeptDO> depts = deptService.getDeptListByLeaderUserId(id);
        if (CollUtil.isEmpty(depts)) {
            return Collections.emptyList();
        }
        // 1.2 获取所有子部门
        Set<Long> deptIds = convertSet(depts, DeptDO::getId);
        List<DeptDO> childDeptList = deptService.getChildDeptList(deptIds);
        if (CollUtil.isNotEmpty(childDeptList)) {
            deptIds.addAll(convertSet(childDeptList, DeptDO::getId));
        }

        // 2. 获取部门对应的用户信息
        List<AdminUserDO> users = userService.getUserListByDeptIds(deptIds);
        users.removeIf(item -> ObjUtil.equal(item.getId(), id)); // 排除自己
        return BeanUtils.toBean(users, AdminUserRespDTO.class);
    }

    @Override
    public List<AdminUserRespDTO> getUserList(Collection<Long> ids) {
        return DataPermissionUtils.executeIgnore(() -> { // 禁用数据权限。原因是，一般基于指定 id 的 API 查询，都是数据拼接为主
            List<AdminUserDO> users = userService.getUserList(ids);
            return BeanUtils.toBean(users, AdminUserRespDTO.class);
        });
    }

    @Override
    public List<AdminUserRespDTO> getUserListByStatus(Integer status) {
        List<AdminUserDO> users = userService.getUserListByStatus(status);
        return BeanUtils.toBean(users, AdminUserRespDTO.class);
    }

    @Override
    public List<AdminUserRespDTO> getUserListByDeptIds(Collection<Long> deptIds) {
        List<AdminUserDO> users = userService.getUserListByDeptIds(deptIds);
        return BeanUtils.toBean(users, AdminUserRespDTO.class);
    }

    @Override
    public List<AdminUserRespDTO> getUserListByPostIds(Collection<Long> postIds) {
        List<AdminUserDO> users = userService.getUserListByPostIds(postIds);
        return BeanUtils.toBean(users, AdminUserRespDTO.class);
    }

    @Override
    public List<AdminUserRespDTO> getUserListByNickname(String nickname) {
        List<AdminUserDO> users = userService.getUserListByNickname(nickname);
        return BeanUtils.toBean(users, AdminUserRespDTO.class);
    }

    @Override
    public void updateUserStatus(Long id, Integer status, String reason) {
        userService.updateUserStatus(id, status);
    }

    @Override
    public void validateUserList(Collection<Long> ids) {
        userService.validateUserList(ids);
    }

}
