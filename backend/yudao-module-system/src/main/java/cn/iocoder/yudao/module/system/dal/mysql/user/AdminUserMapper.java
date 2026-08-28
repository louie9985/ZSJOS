package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {

    @Select("""
            <script>
            SELECT u.*
              FROM system_users u
             WHERE u.deleted = 0 AND u.status = 0
               <if test="keyword != null and keyword != ''">
                 AND (u.nickname LIKE CONCAT('%', #{keyword}, '%')
                      OR u.username LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="mode == 'DEPARTMENT' or mode == 'ROLE_AND_DEPARTMENT'">
                 AND u.dept_id IN
                 <foreach collection="deptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
               </if>
               <if test="mode == 'ROLE' or mode == 'ROLE_AND_DEPARTMENT'">
                 AND EXISTS (
                       SELECT 1 FROM system_user_role ur
                       JOIN system_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 0
                       WHERE ur.user_id = u.id AND ur.deleted = 0 AND ur.role_id IN
                       <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">#{roleId}</foreach>
                 )
               </if>
             ORDER BY u.nickname ASC, u.id ASC
            </script>
            """)
    IPage<AdminUserDO> selectCandidatePage(IPage<AdminUserDO> page,
            @Param("mode") String mode, @Param("roleIds") Collection<Long> roleIds,
            @Param("deptIds") Collection<Long> deptIds, @Param("keyword") String keyword);

    default AdminUserDO selectByUsername(String username) {
        // V056 keeps login names case-sensitive without using MySQL syntax that the tenant SQL parser cannot parse.
        return selectOne(new QueryWrapper<AdminUserDO>().eq("unique_username", username));
    }

    default AdminUserDO selectByEmail(String email) {
        return selectOne(AdminUserDO::getEmail, email);
    }

    default AdminUserDO selectByMobile(String mobile) {
        return selectOne(AdminUserDO::getMobile, mobile);
    }

    default PageResult<AdminUserDO> selectPage(UserPageReqVO reqVO, Collection<Long> deptIds, Collection<Long> userIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AdminUserDO>()
                .likeIfPresent(AdminUserDO::getUsername, reqVO.getUsername())
                .likeIfPresent(AdminUserDO::getMobile, reqVO.getMobile())
                .eqIfPresent(AdminUserDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AdminUserDO::getCreateTime, reqVO.getCreateTime())
                .inIfPresent(AdminUserDO::getDeptId, deptIds)
                .inIfPresent(AdminUserDO::getId, userIds)
                .orderByDesc(AdminUserDO::getId));
    }

    default List<AdminUserDO> selectListByNickname(String nickname) {
        return selectList(new LambdaQueryWrapperX<AdminUserDO>().like(AdminUserDO::getNickname, nickname));
    }

    default List<AdminUserDO> selectListByStatus(Integer status) {
        return selectListByStatusAndDeptId(status, null);
    }

    default List<AdminUserDO> selectListByStatusAndDeptId(Integer status, Long deptId) {
        return selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .eq(AdminUserDO::getStatus, status)
                .eqIfPresent(AdminUserDO::getDeptId, deptId));
    }

    default List<AdminUserDO> selectListByDeptIds(Collection<Long> deptIds) {
        return selectList(AdminUserDO::getDeptId, deptIds);
    }

}
