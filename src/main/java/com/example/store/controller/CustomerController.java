package com.example.store.controller;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @GetMapping
    public PagedModel<CustomerDTO> getAllCustomers(
            @RequestParam(required = false) String query, @PageableDefault(sort = "id") Pageable pageable) {
        Page<Customer> page =
                (query == null || query.isBlank())
                        ? customerRepository.findAll(pageable)
                        : customerRepository.findByNameContaining(query, pageable);

        List<Long> ids = page.getContent().stream().map(Customer::getId).toList();
        List<Customer> withOrders = customerRepository.findAllWithOrdersByIdIn(ids);
        return PageSupport.toPagedModel(page, Customer::getId, withOrders, customerMapper::customerToCustomerDTO);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO createCustomer(@RequestBody CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setName(request.getName());
        return customerMapper.customerToCustomerDTO(customerRepository.save(customer));
    }
}
