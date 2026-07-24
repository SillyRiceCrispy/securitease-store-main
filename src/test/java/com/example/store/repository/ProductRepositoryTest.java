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
class ProductRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void fetchJoinsOrdersForASingleProduct() {
        Customer customer = new Customer();
        customer.setName("Buyer");
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

        Optional<Product> found = productRepository.findByIdWithOrders(product.getId());

        assertThat(found).isPresent();
        assertThat(Hibernate.isInitialized(found.get().getOrders())).isTrue();
        assertThat(found.get().getOrders()).extracting(Order::getDescription).containsExactly("an order");
    }

    @Test
    void distinctPreventsDuplicateProductRowsWhenAProductAppearsInMultipleOrders() {
        Customer customer = new Customer();
        customer.setName("Repeat Buyer");
        entityManager.persist(customer);
        Product product = new Product();
        product.setDescription("popular product");
        entityManager.persist(product);
        Order first = new Order();
        first.setDescription("first order");
        first.setCustomer(customer);
        first.getProducts().add(product);
        entityManager.persist(first);
        Order second = new Order();
        second.setDescription("second order");
        second.setCustomer(customer);
        second.getProducts().add(product);
        entityManager.persistAndFlush(second);
        entityManager.clear();

        List<Product> found = productRepository.findAllWithOrdersByIdIn(List.of(product.getId()));

        // The join against order_product naturally produces one row per (product, order) pair -
        // without DISTINCT this product would come back twice, once per order.
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getOrders()).hasSize(2);
    }
}
