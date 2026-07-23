package com.example.store.controller;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.entity.Order;
import com.example.store.mapper.OrderMapper;
import com.example.store.service.OrderService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping
    public PagedModel<OrderDTO> getAllOrders(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<Order> page = orderService.getOrders(pageable);
        return new PagedModel<>(page.map(orderMapper::orderToOrderDTO));
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderById(@PathVariable Long id) {
        return orderMapper.orderToOrderDTO(orderService.getOrderById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrder(@RequestBody CreateOrderRequest request) {
        Order order =
                orderService.createOrder(request.getDescription(), request.getCustomerId(), request.getProductIds());
        return orderMapper.orderToOrderDTO(order);
    }
}
