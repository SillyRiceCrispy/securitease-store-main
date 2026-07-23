package com.example.store.controller;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public PagedModel<OrderDTO> getAllOrders(@PageableDefault(sort = "id") Pageable pageable) {
        Page<Order> page = orderRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Order::getId).toList();
        List<Order> withDetails = orderRepository.findAllWithCustomerAndProductsByIdIn(ids);
        return PageSupport.toPagedModel(page, Order::getId, withDetails, orderMapper::orderToOrderDTO);
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderById(@PathVariable Long id) {
        Order order = orderRepository
                .findByIdWithCustomerAndProducts(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return orderMapper.orderToOrderDTO(order);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrder(@RequestBody CreateOrderRequest request) {
        if (request.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        Customer customer = customerRepository
                .findById(request.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));

        List<Long> productIds = request.getProductIds() == null ? List.of() : request.getProductIds();
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != Set.copyOf(productIds).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more products not found");
        }

        Order order = new Order();
        order.setDescription(request.getDescription());
        order.setCustomer(customer);
        order.setProducts(products);

        return orderMapper.orderToOrderDTO(orderRepository.save(order));
    }
}
