package com.example.store.controller;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@ComponentScan(basePackageClasses = CustomerMapper.class)
@RequiredArgsConstructor
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private ProductRepository productRepository;

    private Order order;
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setName("John Doe");
        customer.setId(1L);

        product = new Product();
        product.setId(1L);
        product.setDescription("Widget");

        order = new Order();
        order.setDescription("Test Order");
        order.setId(1L);
        order.setCustomer(customer);
        order.setProducts(List.of(product));
    }

    @Test
    void testCreateOrder() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));

        Order toSave = new Order();
        toSave.setDescription("Test Order");
        when(orderRepository.save(toSave)).thenReturn(order);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Test Order");
        request.setCustomerId(1L);
        request.setProductIds(List.of(1L));

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.name").value("John Doe"))
                .andExpect(jsonPath("$.products[0].description").value("Widget"));
    }

    @Test
    void testCreateOrderMissingCustomerId() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Test Order");
        request.setProductIds(List.of());

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateOrderCustomerNotFound() throws Exception {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Test Order");
        request.setCustomerId(99L);
        request.setProductIds(List.of());

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateOrderProductNotFound() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(99L))).thenReturn(List.of());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Test Order");
        request.setCustomerId(1L);
        request.setProductIds(List.of(99L));

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetOrder() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(orderRepository.findAllWithCustomerAndProductsByIdIn(List.of(1L))).thenReturn(List.of(order));

        mockMvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Test Order"))
                .andExpect(jsonPath("$.content[0].customer.name").value("John Doe"))
                .andExpect(jsonPath("$.content[0].products[0].description").value("Widget"));
    }

    @Test
    void testGetOrderById() throws Exception {
        when(orderRepository.findByIdWithCustomerAndProducts(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.name").value("John Doe"));
    }

    @Test
    void testGetOrderByIdNotFound() throws Exception {
        when(orderRepository.findByIdWithCustomerAndProducts(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/order/99")).andExpect(status().isNotFound());
    }
}
