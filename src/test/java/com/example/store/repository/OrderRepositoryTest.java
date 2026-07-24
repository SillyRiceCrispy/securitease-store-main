package com.example.store.repository;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OrderRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void fetchJoinsCustomerAndProductsForASingleOrder() {
        Customer customer = new Customer();
        customer.setName("Order Owner");
        entityManager.persist(customer);
        Product product = new Product();
        product.setDescription("a product");
        entityManager.persist(product);
        Order order = new Order();
        order.setDescription("an order");
        order.setCustomer(customer);
        order.getProducts().add(product);
        entityManager.persistAndFlush(order);
        entityManager.clear();

        Optional<Order> found = orderRepository.findByIdWithCustomerAndProducts(order.getId());

        assertThat(found).isPresent();
        assertThat(Hibernate.isInitialized(found.get().getCustomer())).isTrue();
        assertThat(found.get().getCustomer().getName()).isEqualTo("Order Owner");
        assertThat(Hibernate.isInitialized(found.get().getProducts())).isTrue();
        assertThat(found.get().getProducts())
                .extracting(Product::getDescription)
                .containsExactly("a product");
    }

    @Test
    void distinctPreventsDuplicateOrderRowsWhenAnOrderHasMultipleProducts() {
        Customer customer = new Customer();
        customer.setName("Multi Product Owner");
        entityManager.persist(customer);
        Product first = new Product();
        first.setDescription("first product");
        entityManager.persist(first);
        Product second = new Product();
        second.setDescription("second product");
        entityManager.persist(second);
        Order order = new Order();
        order.setDescription("order with two products");
        order.setCustomer(customer);
        order.getProducts().addAll(List.of(first, second));
        entityManager.persistAndFlush(order);
        entityManager.clear();

        List<Order> found = orderRepository.findAllWithCustomerAndProductsByIdIn(List.of(order.getId()));

        // The join against order_product naturally produces one row per (order, product) pair -
        // without DISTINCT this order would come back twice, once per product.
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getProducts()).hasSize(2);
    }
}
