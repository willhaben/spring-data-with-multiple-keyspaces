package at.naskilla.keyspaces;

import at.naskilla.keyspaces.keyspace1.A;
import at.naskilla.keyspaces.keyspace1.ARepository;
import at.naskilla.keyspaces.keyspace2.B;
import at.naskilla.keyspaces.keyspace2.BRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RepositoriesIT {

    @Container
    public static final CassandraContainer cassandra
            = (CassandraContainer) new CassandraContainer("cassandra:3.11.2").withExposedPorts(9042);

    static {
        cassandra.start();
    }

    @Autowired
    private ARepository aRepository;

    @Autowired
    private BRepository bRepository;

    @BeforeAll
    static void setupCassandraConnectionProperties() {
        System.setProperty("a.keyspace-name", "a_keyspace");
        System.setProperty("b.keyspace-name", "b_keyspace");
        System.setProperty("cassandra.username", cassandra.getUsername());
        System.setProperty("cassandra.password", cassandra.getPassword());
        System.setProperty("cassandra.schema-action", "CREATE_IF_NOT_EXISTS");
        System.setProperty(
                "cassandra.contact-points",
                "%s:%s".formatted(cassandra.getHost(), cassandra.getMappedPort(9042)));
        System.setProperty("cassandra.local-datacenter", cassandra.getLocalDatacenter());
    }

    @BeforeEach
    void setUp() {
        aRepository.deleteAll();
        bRepository.deleteAll();
    }

    @Test
    void givenValueInsertedIntoA_whenReadAll_thenValueReturned() {
        // Given
        A a = new A(UUID.randomUUID(), "test", "test");
        aRepository.insert(a);

        // When
        List<A> allA = aRepository.findAll();
        List<B> allB = bRepository.findAll();

        // Then
        assertThat(allA)
                .hasSize(1).containsExactly(a);
        assertThat(allB)
                .isEmpty();
    }

    @Test
    void givenValueInsertedIntoB_whenReadAll_thenValueReturned() {
        // Given
        B b = new B(UUID.randomUUID(), "test", "test");
        bRepository.insert(b);

        // When
        List<B> allB = bRepository.findAll();
        List<A> allA = aRepository.findAll();

        // Then
        assertThat(allB)
                .hasSize(1).containsExactly(b);
        assertThat(allA).isEmpty();
    }

    @Test
    void givenValueInsertedOneOrTheOther_whenReadAll_thenValuesReturnedInExpectedKeyspaces() {
        // Given
        A a = new A(UUID.randomUUID(), "test", "test");
        aRepository.insert(a);

        B b = new B(UUID.randomUUID(), "test", "test");
        bRepository.insert(b);

        // When
        List<B> allB = bRepository.findAll();
        List<A> allA = aRepository.findAll();

        // Then
        assertThat(allB)
                .hasSize(1)
                .containsExactly(b);
        assertThat(allA)
                .hasSize(1)
                .containsExactly(a);
    }
}