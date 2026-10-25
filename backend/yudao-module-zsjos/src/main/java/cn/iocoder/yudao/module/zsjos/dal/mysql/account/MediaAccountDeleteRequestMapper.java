package cn.iocoder.yudao.module.zsjos.dal.mysql.account;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX; import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX; import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDeleteRequestDO; import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper; import org.apache.ibatis.annotations.Mapper;
@Mapper public interface MediaAccountDeleteRequestMapper extends BaseMapperX<MediaAccountDeleteRequestDO> {
 default MediaAccountDeleteRequestDO byProcess(String p){return selectOne(new LambdaQueryWrapperX<MediaAccountDeleteRequestDO>().eq(MediaAccountDeleteRequestDO::getProcessInstanceId,p));}
 default MediaAccountDeleteRequestDO byAccount(Long id){return selectOne(new LambdaQueryWrapperX<MediaAccountDeleteRequestDO>().eq(MediaAccountDeleteRequestDO::getAccountId,id).orderByDesc(MediaAccountDeleteRequestDO::getId).last("LIMIT 1"));}
 static String boundedError(String error) {
  if (error == null || error.codePointCount(0, error.length()) <= 1000) return error;
  return error.substring(0, error.offsetByCodePoints(0, 1000));
 }
 default int result(Long id,Integer v,String status,String reason,String error){return update(null,new LambdaUpdateWrapper<MediaAccountDeleteRequestDO>().eq(MediaAccountDeleteRequestDO::getId,id).eq(MediaAccountDeleteRequestDO::getVersion,v).set(MediaAccountDeleteRequestDO::getStatus,status).set(MediaAccountDeleteRequestDO::getResultReason,reason).set(MediaAccountDeleteRequestDO::getLastError,boundedError(error)).set(MediaAccountDeleteRequestDO::getAttemptCount,(v==null?0:v)+1).set(MediaAccountDeleteRequestDO::getVersion,v+1));}
}
