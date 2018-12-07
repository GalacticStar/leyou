package com.leyou.item.service;

import com.leyou.common.exception.LyException;
import com.leyou.item.mapper.SpecGroupMapper;
import com.leyou.item.mapper.SpecParamMapper;
import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpecificationService {
    @Autowired
    private SpecGroupMapper specGroupMapper;

    @Autowired
    private SpecParamMapper specParamMapper;

    /**
     * 根据分类id查询规格组
     * @param cid
     * @return
     */
    public List<SpecGroup> querySpecGroups(Long cid) {
        SpecGroup sg = new SpecGroup();
        sg.setCid(cid);
        List<SpecGroup> list = specGroupMapper.select(sg);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NOT_FOUND,"该分类下暂无规格组或尚未选择分类");
        }
        return list;
    }

    /**
     * 根据规格组id查询规格参数
     *
     * @param cid
     * @param gid
     * @param searching
     * @param generic
     * @return
     */
    public List<SpecParam> querySpecParams(Long gid,Long cid, Boolean searching, Boolean generic) {
        SpecParam sp = new SpecParam();
        sp.setGroupId(gid);
        sp.setCid(cid);
        sp.setSearching(searching);
        sp.setGeneric(generic);
        List<SpecParam> list = specParamMapper.select(sp);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NOT_FOUND,"该分组下没有参数");
        }
        return list;
    }

    /**
     * 根据分类id查找规格组和组内所有参数
     * @param cid
     * @return
     */
    public List<SpecGroup> querySpecsByCid(Long cid) {
        //先查询组
        List<SpecGroup> groups = querySpecGroups(cid);
        //查询当前分类下的所有参数
        List<SpecParam> params = querySpecParams(null, cid, null, null);
        //把param放入一个Map中，key是组id，值是组内所有参数
        Map<Long,List<SpecParam>> map = new HashMap<>();
        for (SpecParam param : params) {
            //判断当前参数所属的组在map中是否存在
            if(!map.containsKey(param.getGroupId())){
                map.put(param.getGroupId(),new ArrayList<>());
            }
            //存param到集合
            map.get(param.getGroupId()).add(param);
        }
        //循环存储param数据
        for (SpecGroup group : groups) {
            group.setParams(map.get(group.getId()));
        }
        return groups;
    }
}