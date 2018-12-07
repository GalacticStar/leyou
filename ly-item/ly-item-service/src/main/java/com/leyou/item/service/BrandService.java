package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.mapper.BrandMapper;
import com.leyou.item.pojo.Brand;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Service
public class BrandService {
    @Autowired
    private BrandMapper brandMapper;

    /**
     * 分页查询品牌信息
     * @param page
     * @param rows
     * @param sortBy
     * @param desc
     * @param key
     * @return
     */
    public PageResult<Brand> queryBrandByPage(
            Integer page,Integer rows,String sortBy,Boolean desc,String key){//key：搜索关键词
        //开始分页
        PageHelper.startPage(page,rows);
        //过滤
        Example example = new Example(Brand.class);
        if (StringUtils.isNotBlank(key)){
            example.createCriteria().orLike("name", "%" + key + "%")
                    .orEqualTo("letter", key.toUpperCase());
        }
        if (StringUtils.isNotBlank(sortBy)){
            //排序
            example.setOrderByClause(desc ? sortBy + " DESC" : sortBy + " ASC");
        }
        //查询
        List<Brand> brands = brandMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(brands)){
            throw new LyException(HttpStatus.NOT_FOUND,"未查询到信息");
        }
        //获取分页对象
        PageInfo<Brand> pageInfo = new PageInfo<>(brands);
        //返回结果
        return new PageResult<>(pageInfo.getTotal(),brands);
    }

    /**
     * 新增品牌
     * @param brand
     * @param cids
     */
    @Transactional //事务
    public void addBrand(Brand brand, List<Long> cids) {
        brand.setId(null);
        int count = brandMapper.insert(brand);
        if (count == 0){
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"品牌新增失败!");
        }
        //新增品牌和分类中间表
        for (Long cid : cids) {
            count = brandMapper.addCategoryBrand(cid, brand.getId());
            if (count == 0) {
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"品牌和分类新增失败！");
            }
        }
    }

    /**
     * 根据品牌id查询品牌
     */
    public Brand queryBrandByBid(Long id) {
        Brand brand = brandMapper.selectByPrimaryKey(id);
        if (brand == null){
            throw new LyException(HttpStatus.NOT_FOUND,"查询的品牌不存在");
        }
        return brand;
    }

    /**
     * 根据分类查询品牌
     * @param cid
     * @return
     */
    public List<Brand> queryBrandByCategory(Long cid) {
        List<Brand> list = this.brandMapper.queryByCategoryId(cid);
        if(CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NO_CONTENT,null);
        }
        return list;
    }

    /**
     * 根据多个id查询品牌集合
     * @param ids
     * @return
     */
    public List<Brand> queryBrandByIds(List<Long> ids) {
        return this.brandMapper.selectByIdList(ids);
    }

    /**
     * 修改品牌
     * @param cids
     * @param brand
     */
    @Transactional
    public void updateBrand(List<Long> cids, Brand brand) {
        //修改品牌
        brandMapper.updateByPrimaryKeySelective(brand);
        //维护中间表
        for (Long cid : cids) {
            brandMapper.updateCategoryBrand(cid,brand.getId());
        }
    }

    /**
     * 删除品牌
     * @param bid
     */
    public void deleteBrand(Long bid) {
        //删除品牌表
        brandMapper.deleteByPrimaryKey(bid);
        //维护中间表
        brandMapper.deleteCategoryBrandByBid(bid);
    }
}