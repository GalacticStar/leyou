package com.leyou.item.service;

import com.leyou.common.exception.LyException;
import com.leyou.item.mapper.CategoryMapper;
import com.leyou.item.pojo.Category;
import com.leyou.item.pojo.ChildCates;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;

    public List<Category> queryCategoryByPid(Long pid) {
        Category category = new Category();
        category.setParentId(pid);
        List<Category> list = this.categoryMapper.select(category);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NOT_FOUND,"该分类无子分类");
        }
        return list;
    }

    public List<Category> queryCategoryByCids(List<Long> ids) {
        List<Category> list = categoryMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NOT_FOUND,"该分类不存在");
        }
        return list;
    }

    public List<Category> queryAllByCid3(Long id) {
        Category c3 = this.categoryMapper.selectByPrimaryKey(id);
        Category c2 = this.categoryMapper.selectByPrimaryKey(c3.getParentId());
        Category c1 = this.categoryMapper.selectByPrimaryKey(c2.getParentId());
        List<Category> categories = Arrays.asList(c1, c2, c3);
        if (CollectionUtils.isEmpty(categories)){
            throw new LyException(HttpStatus.NOT_FOUND,"该分类不存在");
        }
        return categories;
    }

    public List<Category> queryCategoryByBid(Long bid) {
        return this.categoryMapper.queryCategoryByBid(bid);
    }

    public List<ChildCates> queryChilds(Long id) {
        //查询二级分类
        List<Category> c2s = queryCategoryByPid(id);
        //创建一个集合用来封装所有二、三级分类
        List<ChildCates> childCatess = new ArrayList<>();
        //遍历二级分类封装数据，并查询三级分类
        for (Category c2 : c2s) {
            //创建对象封装数据
            ChildCates childCates = new ChildCates();
            childCates.setId(c2.getId());
            childCates.setParentId(c2.getParentId());
            childCates.setIsParent(c2.getIsParent());
            childCates.setName(c2.getName());
            childCates.setSort(c2.getSort());
            //查询旗下所有的三级分类
            List<Category> c3s = queryCategoryByPid(c2.getId());
            childCates.setChildCates(c3s);
            childCatess.add(childCates);
        }
        return childCatess;
    }
}