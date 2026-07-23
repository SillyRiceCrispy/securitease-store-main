package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @CircuitBreaker(name = "database")
    @Retry(name = "database")
    @Transactional(readOnly = true)
    public Page<Customer> getCustomers(String query, Pageable pageable) {
        Page<Customer> page = (query == null || query.isBlank())
                ? customerRepository.findAll(pageable)
                : customerRepository.findByNameContaining(query, pageable);

        List<Long> ids = page.getContent().stream().map(Customer::getId).toList();
        List<Customer> withOrders = customerRepository.findAllWithOrdersByIdIn(ids);
        return PageSupport.reorder(page, Customer::getId, withOrders);
    }

    @CircuitBreaker(name = "database")
    @Transactional
    public Customer createCustomer(String name) {
        Customer customer = new Customer();
        customer.setName(name);
        return customerRepository.save(customer);
    }
}
