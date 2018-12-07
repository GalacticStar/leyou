package com.leyou.item.controller;

import com.leyou.item.pojo.Category;
import com.leyou.item.pojo.ChildCates;
import com.leyou.item.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    /*根据父节点查询商品类目*/
    @GetMapping("list")
    public ResponseEntity<List<Category>> queryCategoryByPid(@RequestParam("pid") Long pid) {
        return ResponseEntity.ok(categoryService.queryCategoryByPid(pid));
    }

    /**
     * 根据cid的集合查询分类
     * @param ids
     * @return
     */
    @GetMapping("list/ids")
    public ResponseEntity<List<Category>> queryCategoryByCids(@RequestParam("ids") List<Long> ids) {
        List<Category> list = this.categoryService.queryCategoryByCids(ids);
        return ResponseEntity.ok(list);
    }

    @GetMapping("all/level")
    public ResponseEntity<List<Category>> queryAllByCid3(@RequestParam("id") Long id) {
        List<Category> categories = this.categoryService.queryAllByCid3(id);
        return ResponseEntity.ok(categories);
    }

    /**
     * 根据品牌id查询商品分类
     * @param bid
     * @return
     */
    @GetMapping("bid/{bid}")
    public ResponseEntity<List<Category>> queryCategoryByBid(@PathVariable("bid") Long bid) {
        List<Category> categories = this.categoryService.queryCategoryByBid(bid);
        if (categories == null || categories.size() < 1) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(categories);
    }

    /**
     * 根据顶级分类id查询二、三级分类
     * @param id
     * @return
     */
    @GetMapping("childLists")
    public ResponseEntity<List<ChildCates>> queryChilds(@RequestParam("id") Long id) {
            return ResponseEntity.ok(categoryService.queryChilds(id));
    }
}