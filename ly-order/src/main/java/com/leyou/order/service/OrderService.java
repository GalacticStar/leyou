package com.leyou.order.service;

import com.leyou.auth.pojo.UserInfo;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.IdWorker;
import com.leyou.item.pojo.Sku;
import com.leyou.order.client.AddressClient;
import com.leyou.order.client.GoodsClient;
import com.leyou.order.dto.AddressDTO;
import com.leyou.order.dto.OrderDTO;
import com.leyou.order.enums.OrderStatusEnum;
import com.leyou.order.enums.PayState;
import com.leyou.order.enums.PayStatusEnum;
import com.leyou.order.interceptor.LoginInterceptor;
import com.leyou.order.mapper.OrderDetailMapper;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.mapper.PayLogMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderDetail;
import com.leyou.order.pojo.OrderStatus;
import com.leyou.order.pojo.PayLog;
import com.leyou.order.utils.PayHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private IdWorker idWorker;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper detailMapper;
    @Autowired
    private OrderStatusMapper statusMapper;
    @Autowired
    private PayHelper payHelper;
    @Autowired
    private PayLogService payLogService;
    @Autowired
    private PayLogMapper logMapper;

    /**
     * 创建订单
     *
     * @param orderDTO
     * @return
     */
    @Transactional
    public Long createOrder(OrderDTO orderDTO) {
        //1.组织订单数据
        Order order = new Order();
        //1.1.订单id
        long orderId = idWorker.nextId();
        //1.2.基本信息
        order.setOrderId(orderId);
        order.setCreateTime(new Date());
        order.setPaymentType(orderDTO.getPaymentType());
        //1.3.组织用户数据
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        order.setUserId(userInfo.getId());
        order.setBuyerNick(userInfo.getUsername());
        order.setBuyerRate(false);//是否已经评价
        //TODO 买家留言
        //1.4.收货人信息
        AddressDTO address = AddressClient.findById(orderDTO.getAddressId());
        order.setReceiver(address.getName());
        order.setReceiverAddress(address.getAddress());
        order.setReceiverCity(address.getCity());
        order.setReceiverDistrict(address.getDistrict());
        order.setReceiverMobile(address.getPhone());
        order.setReceiverState(address.getState());
        order.setReceiverZip(address.getZipCode());
        //1.5.订单金额
        //获取id
        Map<Long, Integer> skuNumMap = orderDTO.getCarts().stream()
                .collect(Collectors.toMap(c -> c.getSkuId(), c -> c.getNum()));
        //查询商品
        List<Sku> skus = goodsClient.querySkuByIds(new ArrayList<>(skuNumMap.keySet()));
        //定义金额
        long totalPay = 0;

        //2.新增orderDetail
        List<OrderDetail> details = new ArrayList<>();
        for (Sku sku : skus) {
            Integer num = skuNumMap.get(sku.getId());
            totalPay += sku.getPrice() * num;
            //组织OrderDetail
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(orderId);
            detail.setImage(StringUtils.substringBefore(sku.getImages(), ","));
            detail.setNum(num);
            detail.setSkuId(sku.getId());
            detail.setPrice(sku.getPrice());
            detail.setTitle(sku.getTitle());
            detail.setOwnSpec(sku.getOwnSpec());
            details.add(detail);
        }
        order.setTotalPay(totalPay);
        order.setPostFee(0L);//TODO 结合物流计算，暂时全场包邮
        order.setActualPay(totalPay + order.getPostFee());//TODO 还要减去优惠

        // 新增订单
        orderMapper.insertSelective(order);
        //新增OrderDetail
        detailMapper.insertList(details);

        //3.新增OrderStatus
        OrderStatus status = new OrderStatus();
        status.setOrderId(orderId);
        status.setCreateTime(new Date());
        status.setStatus(OrderStatusEnum.INIT.value());
        statusMapper.insertSelective(status);

        //4.减库存,这里涉及乐观锁
        goodsClient.decreaseStock(orderDTO.getCarts());
        return orderId;
    }

    /**
     * 生成支付url
     *
     * @param orderId
     * @return
     */
    public String getPayUrl(Long orderId) {
        //查询订单
        Order order = orderMapper.selectByPrimaryKey(orderId);
        //校验订单状态
        OrderStatus orderStatus = statusMapper.selectByPrimaryKey(orderId);
        if (orderStatus.getStatus() != OrderStatusEnum.INIT.value()) {
            throw new LyException(HttpStatus.BAD_REQUEST, "订单状态不正确！");
        }
        //生成链接，实付金额应是order.getActualPay(),测试写1，为1分
        String url = payHelper.createPayUrl(orderId, 1L, "乐优商城测试");
        //生成支付日志
        payLogService.createPayLog(orderId, order.getActualPay());
        return url;
    }

    @Transactional
    public void handleNotify(Map<String, String> request) {
        payHelper.handleNotify(request);
    }

    /**
     * 根据orderId查询订单
     *
     * @param orderId
     * @return
     */
    public Order queryOrderById(Long orderId) {
        // 查询订单
        Order order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            throw new LyException(HttpStatus.BAD_REQUEST, "订单不存在");
        }
        // 订单详情
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(orderId);
        List<OrderDetail> orderDetails = detailMapper.select(detail);
        order.setOrderDetails(orderDetails);
        // 订单状态
        OrderStatus orderStatus = statusMapper.selectByPrimaryKey(orderId);
        order.setOrderStatus(orderStatus);
        return order;
    }

    /**
     * 获取订单状态
     * @param orderId
     * @return
     */
    public PayState queryOrderState(Long orderId) {
        // 去数据库查询
        PayLog log = logMapper.selectByPrimaryKey(orderId);
        if (log == null || PayStatusEnum.NOT_PAY.value() == log.getStatus()) {
            // log为空，或者未支付，可能是微信的回调失败！我们应该主动去微信查询
            return payHelper.queryPayState(orderId);
        }
        if (PayStatusEnum.SUCCESS.value() == log.getStatus()) {
            return PayState.SUCCESS;
        }
        return PayState.FAIL;
    }
}