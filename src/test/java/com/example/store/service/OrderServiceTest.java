package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Business-rule tests for order creation - no Spring context, no MockMvc. This is the payoff of moving
 * orchestration/validation out of the controller: these rules are now verifiable in isolation from the web layer.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setName("John Doe");

        product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
    }

    @Test
    void createOrderRejectsMissingCustomerId() {
        assertThatThrownBy(() -> orderService.createOrder("desc", null, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatusCode.valueOf(400));

        verifyNoInteractions(orderRepository, customerRepository, productRepository);
    }

    @Test
    void createOrderRejectsUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder("desc", 99L, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatusCode.valueOf(400));
    }

    @Test
    void createOrderRejectsUnknownProduct() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder("desc", 1L, List.of(99L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatusCode.valueOf(400));
    }

    @Test
    void createOrderToleratesDuplicateProductIds() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(anyList())).thenReturn(List.of(product));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createOrder("desc", 1L, List.of(1L, 1L));

        assertThat(created.getCustomer()).isEqualTo(customer);
        assertThat(created.getProducts()).containsExactly(product);
    }

    @Test
    void createOrderSucceedsWithValidCustomerAndProducts() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createOrder("A new order", 1L, List.of(1L));

        assertThat(created.getDescription()).isEqualTo("A new order");
        assertThat(created.getCustomer()).isEqualTo(customer);
        assertThat(created.getProducts()).containsExactly(product);
    }
}
