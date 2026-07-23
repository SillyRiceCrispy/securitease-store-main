package com.example.store.controller;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.service.CustomerService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    @GetMapping
    public PagedModel<CustomerDTO> getAllCustomers(
            @RequestParam(required = false) String query, @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<Customer> page = customerService.getCustomers(query, pageable);
        return new PagedModel<>(page.map(customerMapper::customerToCustomerDTO));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO createCustomer(@RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.createCustomer(request.getName());
        return customerMapper.customerToCustomerDTO(customer);
    }
}
