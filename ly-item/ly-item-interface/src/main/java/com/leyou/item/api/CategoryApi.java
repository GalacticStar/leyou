package com.leyou.item.api;

import com.leyou.item.pojo.Category;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequestMapping("category")
public interface CategoryApi {
    /**
     * 根据cid的集合查询分类
     */
    @GetMapping("/list/ids")
    List<Category> queryCategoryByCids(@RequestParam("ids") List<Long> ids);
}
