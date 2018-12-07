package com.leyou.item.mapper;

import com.leyou.item.pojo.Brand;
import org.apache.ibatis.annotations.*;
import tk.mybatis.mapper.additional.idlist.SelectByIdListMapper;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface BrandMapper extends Mapper<Brand>, SelectByIdListMapper<Brand,Long> {
    @Insert("INSERT INTO tb_category_brand (category_id,brand_id) VALUES (#{cid},#{bid})")
    int addCategoryBrand(@Param("cid") Long cid,@Param("bid") Long id);

    @Select("SELECT b.* FROM tb_category_brand cb LEFT JOIN tb_brand b ON b.id = cb.brand_id WHERE cb.category_id = #{cid}")
    List<Brand> queryByCategoryId(@Param("cid") Long cid);

    @Update("UPDATE tb_category_brand SET category_id = #{cid} WHERE brand_id = #{bid}")
    void updateCategoryBrand(@Param("cid") Long cid, @Param("bid") Long bid);

    @Delete("DELETE from tb_category_brand WHERE brand_id = #{bid}")
    void deleteCategoryBrandByBid(@Param("bid") Long bid);
}