package com.leyou.search.client;

import com.leyou.common.vo.PageResult;
import com.leyou.item.pojo.Spu;
import com.leyou.search.pojo.Goods;
import com.leyou.search.repository.GoodsRepository;
import com.leyou.search.service.SearchService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.apache.commons.collections4.CollectionUtils; //这个包要注意，导错就出问题

import java.util.List;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ElasticsearchTest {
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private SearchService searchService;

    @Test
    public void testCreate() {
        elasticsearchTemplate.createIndex(Goods.class);
        elasticsearchTemplate.putMapping(Goods.class);
    }

    @Test
    public void testLoadData() {
        int page = 1, rows = 100, size = 0;
        do {
            PageResult<Spu> result = goodsClient.querySpuByPage(null, true, page, rows);
            List<Spu> spus = result.getItems();
            if (CollectionUtils.isEmpty(spus)) {
                break;
            }
            List<Goods> goodsList = spus.stream()
                    .map(spu -> searchService.buildGoods(spu)).collect(Collectors.toList());
            goodsRepository.saveAll(goodsList);
            size = spus.size();
            page++;
        } while (size == 100);
    }
}