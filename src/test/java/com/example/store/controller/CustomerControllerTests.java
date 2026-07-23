package com.example.store.controller;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@ComponentScan(basePackageClasses = CustomerMapper.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setName("John Doe");
        customer.setId(1L);
    }

    @Test
    void testCreateCustomer() throws Exception {
        when(customerService.createCustomer("John Doe")).thenReturn(customer);

        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setName("John Doe");

        mockMvc.perform(post("/v1/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testGetAllCustomers() throws Exception {
        Page<Customer> page = new PageImpl<>(List.of(customer));
        when(customerService.getCustomers(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("John Doe"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void testSearchCustomersByNameSubstring() throws Exception {
        Page<Customer> page = new PageImpl<>(List.of(customer));
        when(customerService.getCustomers(eq("oh"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/customer").param("query", "oh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("John Doe"));
    }

    @Test
    void testSearchCustomersByNameSubstringNoMatch() throws Exception {
        Page<Customer> page = new PageImpl<>(List.of());
        when(customerService.getCustomers(eq("zzz"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/customer").param("query", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
