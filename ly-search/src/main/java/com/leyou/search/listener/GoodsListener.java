package com.leyou.search.listener;

import com.leyou.search.service.SearchService;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoodsListener {
    @Autowired
    private SearchService searchService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "ly.search.insert.queue",durable = "true"),
            exchange = @Exchange(
                    name = "ly.item.exchange",type = ExchangeTypes.TOPIC,ignoreDeclarationExceptions = "true"),
            key = {"item.insert","item.update"}
    ))
    public void listenInsertOrUpdate(Long spuId){
        if (spuId != null){
            //新增或修改
            searchService.insertOrUpdate(spuId);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "ly.search.delete.queue",durable = "true"),
            exchange = @Exchange(
                    name = "ly.item.exchange",type = ExchangeTypes.TOPIC,ignoreDeclarationExceptions = "true"),
            key = "item.delete"
    ))
    public void listenDelete(Long spuId){
        if (spuId != null){
            //删除
            searchService.delete(spuId);
        }
    }
}
