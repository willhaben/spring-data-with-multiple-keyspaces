package at.willhaben.springboot2keyspaces;

import at.willhaben.springboot2keyspaces.global.C;
import at.willhaben.springboot2keyspaces.keyspace1.A;
import at.willhaben.springboot2keyspaces.keyspace1.ARepository;
import at.willhaben.springboot2keyspaces.keyspace1.Keyspace1CRepository;
import at.willhaben.springboot2keyspaces.keyspace2.B;
import at.willhaben.springboot2keyspaces.keyspace2.BRepository;
import at.willhaben.springboot2keyspaces.keyspace2.Keyspace2CRepository;
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

    @Autowired
    private Keyspace1CRepository keyspace1CRepository;
    @Autowired
    private Keyspace2CRepository keyspace2CRepository;

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
        keyspace1CRepository.deleteAll();
        keyspace2CRepository.deleteAll();
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

    @Test
    void givenCValue_whenPersistingValueInKeyspace1_thenValueCanOnlyBeReadInPersistedKeyspace() {
        // Given
        C c = new C(UUID.randomUUID(), "test", "test");
        keyspace1CRepository.insert(c);

        // When
        List<C> allCsInKeyspace1 = keyspace1CRepository.findAll();
        List<C> allCsInKeyspace2 = keyspace2CRepository.findAll();

        // Then
        assertThat(allCsInKeyspace1)
                .containsExactly(c);
        assertThat(allCsInKeyspace2)
                .isEmpty();
    }

    @Test
    void givenCValue_whenPersistingValueInKeyspace2_thenValueCanOnlyBeReadInPersistedKeyspace() {
        // Given
        C c = new C(UUID.randomUUID(), "test", "test");
        keyspace2CRepository.insert(c);

        // When
        List<C> allCsInKeyspace1 = keyspace1CRepository.findAll();
        List<C> allCsInKeyspace2 = keyspace2CRepository.findAll();

        // Then
        assertThat(allCsInKeyspace1)
                .isEmpty();
        assertThat(allCsInKeyspace2)
                .containsExactly(c);
    }

    @Test
    void given2CValues_whenPersistingValueInDifferentKeyspaces_thenValueCanOnlyBeReadInPersistedKeyspace() {
        // Given
        C c1 = new C(UUID.randomUUID(), "test1", "test1");
        keyspace1CRepository.insert(c1);

        C c2 = new C(UUID.randomUUID(), "test2", "test2");
        keyspace2CRepository.insert(c2);

        // When
        List<C> allCsInKeyspace1 = keyspace1CRepository.findAll();
        List<C> allCsInKeyspace2 = keyspace2CRepository.findAll();

        // Then
        assertThat(allCsInKeyspace1)
                .containsExactly(c1);
        assertThat(allCsInKeyspace2)
                .containsExactly(c2);
    }

    @Test
    void given2Value_whenPersistingValueInBothKeyspaces_thenValueCanBeReadInBothKeyspaces() {
        // Given
        C c = new C(UUID.randomUUID(), "test1", "test1");
        keyspace1CRepository.insert(c);
        keyspace2CRepository.insert(c);

        // When
        List<C> allCsInKeyspace1 = keyspace1CRepository.findAll();
        List<C> allCsInKeyspace2 = keyspace2CRepository.findAll();

        // Then
        assertThat(allCsInKeyspace1)
                .containsExactly(c);
        assertThat(allCsInKeyspace2)
                .containsExactly(c);
    }

    @Test
    void given2ValuesWithSameId_whenPersistingValueInBothKeyspaces_thenValueBeReadById() {
        // Given
        UUID commonId = UUID.randomUUID();
        C c1 = new C(commonId, "test1", "test1");
        // Using the same id but different values out side of the keys signifies that we can have the same value but in different keyspaces
        C c2 = new C(commonId, "test1", "test2");
        keyspace1CRepository.insert(c1);
        keyspace2CRepository.insert(c2);

        // When
        C cFromKeyspace1 = keyspace1CRepository.findByA(commonId);
        C cFromKeyspace2 = keyspace2CRepository.findByA(commonId);

        // Then
        assertThat(cFromKeyspace1)
                .isEqualTo(c1);
        assertThat(cFromKeyspace2)
                .isEqualTo(c2);
    }
}