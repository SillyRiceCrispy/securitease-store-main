package com.example.store.controller;

import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.ProductMapper;
import com.example.store.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@ComponentScan(basePackageClasses = ProductMapper.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductRepository productRepository;

    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
        order.setDescription("Test Order");

        product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
        product.setOrders(List.of(order));
    }

    @Test
    void testCreateProduct() throws Exception {
        when(productRepository.save(product)).thenReturn(product);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Widget"))
                .andExpect(jsonPath("$.orders[0]").value(1));
    }

    @Test
    void testGetAllProducts() throws Exception {
        when(productRepository.findAllWithOrders()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Widget"))
                .andExpect(jsonPath("$[0].orders[0]").value(1));
    }

    @Test
    void testGetProductById() throws Exception {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Widget"))
                .andExpect(jsonPath("$.orders[0]").value(1));
    }

    @Test
    void testGetProductByIdNotFound() throws Exception {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/99")).andExpect(status().isNotFound());
    }
}
