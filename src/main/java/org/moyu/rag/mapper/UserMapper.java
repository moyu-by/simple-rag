package org.moyu.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.moyu.rag.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
