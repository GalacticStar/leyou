package com.leyou.search.client;

import com.leyou.item.pojo.Category;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CategoryClientTest {
@Autowired
private CategoryClient categoryClient;
    @Test
    public void queryCategoryByCids() {
        List<Category> list = categoryClient.queryCategoryByCids(Arrays.asList(1L, 2L, 3L));
        for (Category category : list) {
            System.out.println("category = " + category);
        }
        Assert.assertEquals(3,list.size());
    }
}