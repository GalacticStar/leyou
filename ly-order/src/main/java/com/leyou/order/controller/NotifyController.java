package com.leyou.order.controller;

import com.leyou.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class NotifyController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/wxpay/notify")
    public ResponseEntity<String> callback(@RequestBody Map<String, String> request) {
        System.out.println(request);
        orderService.handleNotify(request);
        String msg = "<xml>\n" +
                "\n" +
                "  <return_code><![CDATA[SUCCESS]]></return_code>\n" +
                "  <return_msg><![CDATA[OK]]></return_msg>\n" +
                "</xml>";
        return ResponseEntity.ok(msg);
    }
}