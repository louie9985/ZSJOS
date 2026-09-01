package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCreateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserOrganizationUpdateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserPartnerConversionReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCandidatePageReqDTO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Admin 用户 API 接口
 *
 * @author 芋道源码
 */
public interface AdminUserApi {

    /** Pages enabled users matching a role/department qualification in the owning System database. */
    PageResult<AdminUserRespDTO> getCandidateUserPage(AdminUserCandidatePageReqDTO reqDTO);

    Long createUser(AdminUserCreateReqDTO reqDTO);

    void updateUserOrganization(AdminUserOrganizationUpdateReqDTO reqDTO);

    Long convertPartnerToEmployee(AdminUserPartnerConversionReqDTO reqDTO);

    /**
     * 通过用户 ID 查询用户
     *
     * @param id 用户ID
     * @return 用户对象信息
     */
    AdminUserRespDTO getUser(Long id);

    /**
     * 通过手机号查询用户。
     */
    AdminUserRespDTO getUserByMobile(String mobile);

    /**
     * 通过用户 ID 查询用户下属
     *
     * @param id 用户编号
     * @return 用户下属用户列表
     */
    List<AdminUserRespDTO> getUserListBySubordinate(Long id);

    /**
     * 通过用户 ID 查询用户们
     *
     * @param ids 用户 ID 们
     * @return 用户对象信息
     */
    List<AdminUserRespDTO> getUserList(Collection<Long> ids);

    /**
     * 获得指定状态的用户数组。
     *
     * @param status 用户状态
     * @return 用户数组
     */
    List<AdminUserRespDTO> getUserListByStatus(Integer status);

    /**
     * 获得指定部门的用户数组
     * 跨模块组织花名册查询不受当前调用方数据权限影响，调用方仍需自行应用业务范围。
     *
     * @param deptIds 部门数组
     * @return 用户数组
     */
    List<AdminUserRespDTO> getUserListByDeptIds(Collection<Long> deptIds);

    /**
     * 获得指定岗位的用户数组
     *
     * @param postIds 岗位数组
     * @return 用户数组
     */
    List<AdminUserRespDTO> getUserListByPostIds(Collection<Long> postIds);

    /**
     * 根据昵称模糊搜索用户
     *
     * @param nickname 昵称关键词
     * @return 用户列表
     */
    List<AdminUserRespDTO> getUserListByNickname(String nickname);

    /**
     * 修改用户状态。禁用时由 System 撤销该用户的登录 Token。
     *
     * @param id 用户编号
     * @param status 账号状态
     * @param reason 调用方审计原因
     */
    void updateUserStatus(Long id, Integer status, String reason);

    /**
     * 获得用户 Map
     *
     * @param ids 用户编号数组
     * @return 用户 Map
     */
    default Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids) {
        List<AdminUserRespDTO> users = getUserList(ids);
        return CollectionUtils.convertMap(users, AdminUserRespDTO::getId);
    }

    /**
     * 校验用户是否有效。如下情况，视为无效：
     * 1. 用户编号不存在
     * 2. 用户被禁用
     *
     * @param id 用户编号
     */
    default void validateUser(Long id) {
        validateUserList(Collections.singleton(id));
    }

    /**
     * 校验用户们是否有效。如下情况，视为无效：
     * 1. 用户编号不存在
     * 2. 用户被禁用
     *
     * @param ids 用户编号数组
     */
    void validateUserList(Collection<Long> ids);

}
