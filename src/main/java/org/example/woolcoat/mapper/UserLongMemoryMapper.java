package org.example.woolcoat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.woolcoat.entity.UserLongMemory;

import java.util.List;

/**
 * 用户长期记忆Mapper
 */
@Mapper
public interface UserLongMemoryMapper extends BaseMapper<UserLongMemory> {

    /**
     * 根据用户ID和记忆类型查询长期记忆
     */
    List<UserLongMemory> selectByUserIdAndType(String userId, String memoryType);
}
