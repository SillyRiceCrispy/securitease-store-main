package com.example.store.repository;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CustomerRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsCustomerByCaseInsensitiveSubstringWithinAWordOfTheirName() {
        Customer customer = new Customer();
        customer.setName("Zbigniew Testowski");
        entityManager.persistAndFlush(customer);

        Page<Customer> byMiddleOfSecondWord = customerRepository.findByNameContaining("TOW", PageRequest.of(0, 20));

        assertThat(byMiddleOfSecondWord.getContent())
                .extracting(Customer::getId)
                .contains(customer.getId());
    }

    @Test
    void excludesCustomersThatDoNotMatchTheSubstring() {
        Customer customer = new Customer();
        customer.setName("Completely Unrelated Name");
        entityManager.persistAndFlush(customer);

        Page<Customer> results =
                customerRepository.findByNameContaining("no-such-substring-anywhere", PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Customer::getId).doesNotContain(customer.getId());
    }

    @Test
    void fetchJoinsOrdersSoTheyAreAlreadyInitializedAfterTheOriginalSessionIsGone() {
        Customer customer = new Customer();
        customer.setName("Has Two Orders");
        Order first = new Order();
        first.setDescription("first order");
        first.setCustomer(customer);
        Order second = new Order();
        second.setDescription("second order");
        second.setCustomer(customer);
        customer.getOrders().addAll(List.of(first, second));
        entityManager.persistAndFlush(customer);
        // Detach everything so the repository call below can only see what it actually
        // fetched, not leftover associations still cached in this session's first-level cache.
        entityManager.clear();

        List<Customer> found = customerRepository.findAllWithOrdersByIdIn(List.of(customer.getId()));

        assertThat(found).hasSize(1);
        assertThat(Hibernate.isInitialized(found.get(0).getOrders())).isTrue();
        assertThat(found.get(0).getOrders())
                .extracting(Order::getDescription)
                .containsExactlyInAnyOrder("first order", "second order");
    }
}
