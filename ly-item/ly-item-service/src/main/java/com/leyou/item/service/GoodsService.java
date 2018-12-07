package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.dto.CartDTO;
import com.leyou.item.mapper.SkuMapper;
import com.leyou.item.mapper.SpuDetailMapper;
import com.leyou.item.mapper.SpuMapper;
import com.leyou.item.mapper.StockMapper;
import com.leyou.item.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoodsService {
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SpuDetailMapper spuDetailMapper;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private AmqpTemplate amqpTemplate;

    /**
     * 分页查询商品列表
     * @param key
     * @param saleable
     * @param page
     * @param rows
     * @return
     */
    public PageResult<Spu> querySpuByPage(String key, Boolean saleable, Integer page, Integer rows) {
        //开始分页
        PageHelper.startPage(page,Math.min(rows,200));
        //过滤
        Example example = new Example(Spu.class);
        Example.Criteria criteria = example.createCriteria();
        //过滤逻辑删除
        criteria.andEqualTo("valid",true);
        //搜索条件
        if (StringUtils.isNotBlank(key)){
            criteria.andLike("title","%" + key + "%");
        }
        //上下架
        if (saleable != null){
            criteria.andEqualTo("saleable",saleable);
        }
        //查询结果
        List<Spu> list = spuMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NOT_FOUND,"未查询到信息");
        }
        //查询分类名称和品牌名称
        handleCategoryAndBrandName(list);
        //封装分页结果
        PageInfo<Spu> info = new PageInfo<>(list);
        return new PageResult<>(info.getTotal(),list);
    }

    private void handleCategoryAndBrandName(List<Spu> list) {
        for (Spu spu:list) {
            //查询分类
            List<Category> categories = categoryService.queryCategoryByCids(Arrays.asList(spu.getCid1(),spu.getCid2(),spu.getCid3()));
            if (categories == null){
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"商品所属分类不存在");
            }
            List<String> names = categories.stream().map(c -> c.getName()).collect(Collectors.toList());
            spu.setCname(StringUtils.join(names,"/"));
            //查询品牌
            Brand brand = brandService.queryBrandByBid(spu.getBrandId());
            if (brand == null){
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"商品所属品牌不存在");
            }
            spu.setBname(brand.getName());
        }
    }

    /**
     * 新增商品
     * @param spu
     */
    @Transactional //事务
    public void saveGoods(Spu spu) {
        //保存spu
        spu.setId(null);
        spu.setSaleable(true);
        spu.setValid(true);
        spu.setCreateTime(new Date());
        spu.setLastUpdateTime(spu.getCreateTime());
        this.spuMapper.insert(spu);
        Long spuId = spu.getId();
        //保存spu详情
        spu.getSpuDetail().setSpuId(spuId);
        this.spuDetailMapper.insert(spu.getSpuDetail());
        //保存sku和库存信息
        saveSkuAndStock(spu);
        //发送消息
        sendMessage(spuId,"insert");
    }

    private void saveSkuAndStock(Spu spu) {
        //新增sku
        Long spuId = spu.getId();
        List<Stock> stocks = new ArrayList<>();
        for (Sku sku : spu.getSkus()) {
            sku.setId(null);
            sku.setSpuId(spuId);
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            skuMapper.insert(sku);

            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            stocks.add(stock);
        }
        //新增库存
        stockMapper.insertList(stocks);
    }

    /**
     * 根据spu的id查找spu详细信息
     * @param spuId
     * @return
     */
    public SpuDetail querySpuDetailById(Long spuId) {
        SpuDetail detail = spuDetailMapper.selectByPrimaryKey(spuId);
        if (detail == null){
            throw new LyException(HttpStatus.NOT_FOUND,"商品详情查询失败");
        }
        return detail;
    }

    /**
     * 根据spu的id查找sku列表
     * @param spuId
     * @return
     */
    public List<Sku> querySkuListBySpuId(Long spuId) {
        //查询sku
        Sku sku = new Sku();
        sku.setSpuId(spuId);
        List<Sku> list = skuMapper.select(sku);
        if (CollectionUtils.isEmpty(list)){
            throw new LyException(HttpStatus.NO_CONTENT,"商品查询失败");
        }
        //查询库存
        List<Long> ids = list.stream().map(s -> s.getId()).collect(Collectors.toList());
        List<Stock> stocks = stockMapper.selectByIdList(ids);
        if(CollectionUtils.isEmpty(stocks)){
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"商品库存数据异常");
        }
        //转换数据
        Map<Long,Integer> stockMap = new HashMap<>();
        for (Stock stock : stocks) {
            stockMap.put(stock.getSkuId(),stock.getStock());
        }
        for(Sku s : list){
            s.setStock(stockMap.get(s.getId()));
        }
        return list;
    }

    /**
     * 修改商品
     * @param spu
     */
    @Transactional
    public void updateGoods(Spu spu) {
        Long spuId = spu.getId();
        if (spuId == null){
            throw new LyException(HttpStatus.BAD_REQUEST,"商品id不能为空");
        }
        //先查询以前的sku
        Sku sku = new Sku();
        sku.setSpuId(spuId);
        List<Sku> skus = skuMapper.select(sku);
        if (CollectionUtils.isNotEmpty(skus)){
            //存在，则删除sku
            skuMapper.delete(sku);
            //删除库存
            List<Long> ids = skus.stream().map(s -> s.getId()).collect(Collectors.toList());
            stockMapper.deleteByIdList(ids);
        }
        //修改spu
        spu.setLastUpdateTime(new Date());
        spu.setValid(null);
        spu.setSaleable(null);
        spu.setCreateTime(null);
        spuMapper.updateByPrimaryKeySelective(spu);
        //修改detail
        spuDetailMapper.updateByPrimaryKey(spu.getSpuDetail());
        //新增sku和库存
        saveSkuAndStock(spu);
        //发送消息
        sendMessage(spuId,"update");
    }

    public void sendMessage(Long spuId,String type){
        //发送消息
        try {
            this.amqpTemplate.convertAndSend("item." + type,spuId);
        } catch (Exception e) {
            log.error("消息发送失败,{}",e.getMessage(),e);
            throw new RuntimeException("消息发送失败",e);
        }
    }

    public Spu querySpuById(Long id) {
        //查询spu
        Spu spu = spuMapper.selectByPrimaryKey(id);
        if (spu == null){
            throw new LyException(HttpStatus.NOT_FOUND,"spu查询失败！");
        }
        //查询spu下的sku集合
        spu.setSkus(querySkuListBySpuId(id));
        //查询detail
        spu.setSpuDetail(querySpuDetailById(id));
        return spu;
    }

    /**
     * 购物车中商品列表
     * @param ids
     * @return
     */
    public List<Sku> querySkuByIds(List<Long> ids) {
        //查询sku
        List<Sku> skus = skuMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(skus)){
            throw new LyException(HttpStatus.NOT_FOUND,"商品查询失败！");
        }
        //填充库存
        fillStock(ids,skus);
        return skus;
    }

    /**
     * 根据sku列表查询库存
     * @param ids
     * @param skus
     */
    private void fillStock(List<Long> ids, List<Sku> skus) {
        //查询库存
        List<Stock> stocks = stockMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(stocks)){
            throw new LyException(HttpStatus.NOT_FOUND,"库存查询失败");
        }
        //将库存转为map，key是skuId，值是库存
        Map<Long,Integer> stockMap = stocks.stream().collect(Collectors.toMap(stock -> stock.getSkuId(),stock -> stock.getStock()));
        //保存库存到sku
        for (Sku sku : skus) {
            sku.setStock(stockMap.get(sku.getId()));
        }
    }

    /**
     * 减少已下单商品的库存
     * @param carts
     */
    @Transactional
    public void decreaseStock(List<CartDTO> carts) {
        for (CartDTO cart : carts) {
            //减库存
            int count = stockMapper.decreaseStock(cart.getSkuId(),cart.getNum());
            if (count != 1){
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR,"库存不足");
            }
        }
    }
}