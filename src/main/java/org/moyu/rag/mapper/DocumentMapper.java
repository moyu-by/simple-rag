package org.moyu.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.moyu.rag.entity.Document;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
