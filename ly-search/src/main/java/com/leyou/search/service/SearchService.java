package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.JsonUtils;
import com.leyou.common.utils.NumberUtils;
import com.leyou.common.vo.PageResult;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import com.leyou.search.repository.GoodsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchService {
    @Autowired
    private CategoryClient categoryClient;
    @Autowired
    private BrandClient brandClient;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private SpecificationClient specificationClient;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private ElasticsearchTemplate template;

    public Goods buildGoods(Spu spu) {
        Long spuId = spu.getId();
        // 所有需要被搜索的信息，包含标题，分类，甚至品牌
        //1.查询商品分类
        List<String> names = categoryClient.queryCategoryByCids(
                Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3())).stream()
                .map(c -> c.getName()).collect(Collectors.toList());
        //2.查询品牌
        Brand brand = brandClient.queryBrandByBid(spu.getBrandId());
        //搜索过滤的字段
        String all = spu.getTitle() + " " + StringUtils.join(names, " ") + " " + brand.getName();

        //查询sku
        List<Sku> skus = goodsClient.querySkuBySpuId(spuId);
        //sku的价格的集合
        Set<Long> prices = new HashSet<>();
        //设置sku集合的JSON格式
        List<Map<String, Object>> skuList = new ArrayList<>();
        for (Sku sku : skus) {
            prices.add(sku.getPrice());
            Map<String, Object> map = new HashMap<>();
            map.put("id", sku.getId());
            map.put("image", StringUtils.substringBefore(sku.getImages(), ","));
            map.put("price", sku.getPrice());
            map.put("title", sku.getTitle());
            skuList.add(map);
        }

        //查询规格参数的key
        List<SpecParam> specParams = specificationClient.querySpecParams(null, spu.getCid3(), null, true);
        //查询spuDetail
        SpuDetail detail = goodsClient.querySpuDetailById(spuId);
        //通用规格参数值
        Map<String, Object> genericSpec = JsonUtils.nativeRead(detail.getGenericSpec(), new TypeReference<Map<String, Object>>() {
        });
        //特有规格参数值
        Map<String, List<String>> specialSpec = JsonUtils.nativeRead(detail.getSpecialSpec(), new TypeReference<Map<String, List<String>>>() {
        });
        //可搜索的规格参数键值对
        Map<String, Object> specs = new HashMap<>();
        for (SpecParam param : specParams) {
            String key = param.getName();
            Object value = null;
            if (param.getGeneric()) {
                //通用参数
                value = genericSpec.get(param.getId().toString());
                //判断是否为数值类型
                if (param.getNumeric()) {
                    value = chooseSegment(value.toString(), param);
                }
            } else {
                //特有参数
                value = specialSpec.get(param.getId().toString());
            }
            if (value == null) {
                value = "其它";
            }
            specs.put(key, value);
        }

        Goods goods = new Goods();
        goods.setId(spuId);
        goods.setAll(all);
        goods.setBrandId(spu.getBrandId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setCreateTime(spu.getCreateTime());
        goods.setPrice(prices);
        goods.setSkus(JsonUtils.serialize(skuList));
        goods.setSpecs(specs);
        goods.setSubTitle(spu.getSubTitle());
        return goods;
    }

    private String chooseSegment(String value, SpecParam param) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        //保存数值段
        for (String segment : param.getSegments().split(",")) {
            String[] segs = segment.split("-");
            //获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if (segs.length == 2) {
                end = NumberUtils.toDouble(segs[1]);
            }
            //判断是否在范围内
            if (val >= begin && val < end) {
                if (segs.length == 1) {
                    result = segs[0] + param.getUnit() + "以上";
                } else if (begin == 0) {
                    result = segs[1] + param.getUnit() + "以下";
                } else {
                    result = segment + param.getUnit();
                }
                break;
            }
        }
        return result;
    }

    public PageResult<Goods> search(SearchRequest request) {
        String key = request.getKey();
        if (StringUtils.isBlank(key)) {
            // 没有查询条件
            throw new LyException(HttpStatus.BAD_REQUEST, "查询条件不能为空");
        }
        // 原生查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // 0、控制返回结果字段
        queryBuilder.withSourceFilter(
                new FetchSourceFilter(new String[]{"id", "subTitle", "skus"}, null));
        // 1、分页和排序
        searchWithPageAndSort(queryBuilder, request);
        // 2、基本搜索条件
        QueryBuilder basicQuery = buildqueryBuilder(request);
        queryBuilder.withQuery(basicQuery);
        // 3、对分类和品牌聚合
        String categoryAggName = "categoryAgg";
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        String brandAggName = "brandAgg";
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));

        // 4、搜索
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);

        // 5、解析结果
        // 5.1、解析聚合结果
        Aggregations aggs = result.getAggregations();
        List<Category> categories = handleCategoryAgg(aggs.get(categoryAggName));
        List<Brand> brands = handleBrandAgg(aggs.get(brandAggName));

        // 5.2、对规格参数聚合
        List<Map<String, Object>> specs = null;
        // 判断分类数量是否为1
        if (categories != null && categories.size() == 1) {
            specs = handleSpecs(categories.get(0).getId(), basicQuery);
        }

        // 5.3、解析分页结果
        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        List<Goods> list = result.getContent();

        return new SearchResult(total, Long.valueOf(totalPages), list, categories, brands, specs);
    }

    private QueryBuilder buildqueryBuilder(SearchRequest request) {
        // 构建布尔查询
        BoolQueryBuilder basicQuery = QueryBuilders.boolQuery();
        // 搜索条件
        basicQuery.must(QueryBuilders.matchQuery("all", request.getKey()));
        // 过滤条件
        Map<String, String> filterMap = request.getFilter();
        for (Map.Entry<String, String> entry : filterMap.entrySet()) {
            // 过滤字段
            String key = entry.getKey();
            if (!"cid3".equals(key) && !"brandId".equals(key)) {
                key = "specs." + key + ".keyword";
            }
            // 过滤条件
            String value = entry.getValue();
            // 因为是keyword类型，所以使用term查询
            basicQuery.filter(QueryBuilders.termQuery(key, value));
        }
        return basicQuery;
    }

    private List<Map<String, Object>> handleSpecs(Long cid, QueryBuilder basicBuilder) {
        List<Map<String, Object>> specs = new ArrayList<>();
        // 1、查询可过滤的规格参数
        List<SpecParam> params = specificationClient.querySpecParams(null,cid,true,true);
        // 2、聚合规格参数
        // 2.1 基本查询条件
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        queryBuilder.withQuery(basicBuilder);
        queryBuilder.withPageable(PageRequest.of(0, 1));
        // 2.2 聚合
        for (SpecParam param : params) {
            String name = param.getName();
            queryBuilder.addAggregation(AggregationBuilders.terms(name).field("specs." + name + ".keyword"));
        }
        // 3、查询
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);

        // 4、解析结果
        Aggregations aggs = result.getAggregations();
        for (SpecParam param : params) {
            StringTerms terms = aggs.get(param.getName());
            // 创建聚合结果
            Map<String, Object> map = new HashMap<>();
            map.put("k", param.getName());
            map.put("options", terms.getBuckets().stream().map(b -> b.getKeyAsString()).collect(Collectors.toList()));
            specs.add(map);
        }
        // 4、封装
        return specs;
    }

    private List<Brand> handleBrandAgg(LongTerms terms) {
        try {
            // 获取id
            List<Long> ids = terms.getBuckets().stream()
                    .map(b -> b.getKeyAsNumber().longValue()).collect(Collectors.toList());
            // 查询
            List<Brand> brands = brandClient.queryBrandByIds(ids);
            return brands;
        } catch (Exception e) {
            log.error("查询品牌信息失败", e);
            return null;
        }
    }

    private List<Category> handleCategoryAgg(LongTerms terms) {
        try {
            // 获取id
            List<Long> ids = terms.getBuckets().stream()
                    .map(b -> b.getKeyAsNumber().longValue()).collect(Collectors.toList());
            // 查询
            List<Category> categories = categoryClient.queryCategoryByCids(ids);
            return categories;
        } catch (Exception e) {
            log.error("查询分类信息失败", e);
            return null;
        }
    }

    // 构建基本查询条件
    private void searchWithPageAndSort(NativeSearchQueryBuilder queryBuilder, SearchRequest request) {
        // 准备分页参数
        int page = request.getPage();
        int size = request.getSize();
        // 1、分页
        queryBuilder.withPageable(PageRequest.of(page - 1, size));
        // 2、排序
        String sortBy = request.getSortBy();
        Boolean desc = request.getDescending();
        if (StringUtils.isNotBlank(sortBy)) {
            // 如果不为空，则进行排序
            queryBuilder.withSort(SortBuilders.fieldSort(sortBy).order(desc ? SortOrder.DESC : SortOrder.ASC));
        }
    }

    //新增或修改
    public void insertOrUpdate(Long spuId) {
        //查询spu
        Spu spu = this.goodsClient.querySpuById(spuId);
        if (spu == null){
            log.error("索引对应的spu不存在,spuId: {}",spuId);
            throw new RuntimeException("索引对应的spu不存在");
        }
        //转为goods
        Goods goods = buildGoods(spu);
        goodsRepository.save(goods);
    }

    //删除
    public void delete(Long spuId) {
        goodsRepository.deleteById(spuId);
    }
}