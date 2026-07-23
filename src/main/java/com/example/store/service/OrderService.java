package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Order> getOrders(Pageable pageable) {
        Page<Order> page = orderRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Order::getId).toList();
        List<Order> withDetails = orderRepository.findAllWithCustomerAndProductsByIdIn(ids);
        return PageSupport.reorder(page, Order::getId, withDetails);
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository
                .findByIdWithCustomerAndProducts(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Order createOrder(String description, Long customerId, List<Long> productIds) {
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));

        List<Long> ids = productIds == null ? List.of() : productIds;
        List<Product> products = productRepository.findAllById(ids);
        if (products.size() != Set.copyOf(ids).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more products not found");
        }

        Order order = new Order();
        order.setDescription(description);
        order.setCustomer(customer);
        order.setProducts(products);

        return orderRepository.save(order);
    }
}
