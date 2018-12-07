package com.leyou.common.mapper;

import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.additional.idlist.IdListMapper;
import tk.mybatis.mapper.additional.insert.InsertListMapper;

/**
 * @author: HuYi.Zhang
 * @create: 2018-08-20 16:55
 **/
public interface BaseMapper<T,PK> extends Mapper<T>, IdListMapper<T, PK>, InsertListMapper<T> {
}