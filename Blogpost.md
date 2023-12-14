# Spring Boot 3.2.x Application using Cassandra With Multiple Keyspaces

## Introduction
Disclaimer: Whether or not multiple keyspaces should be used within a single application is a discussion for a different time. Herein, 
we present the solution we managed to find for the problem at hand.


Here at willhaben, we use AWS Keyspaces to store some of our data. For one of our services we need to aggregate data 
from multiple keysapces. When setting this up in practice, while adding the connection to the second keyspace into our codebase, we ran into an issue: 
Spring Data Cassandra does not support using multiple keyspaces out of the box - at least not in version 3.2.x.

Although we found resources providing solutions for how to make it work, we still encountered difficulties in setting the connection up 
for various reasons. [This article](https://www.codingame.com/playgrounds/13647/multiple-keyspaces-with-spring-data-cassandra) demonstrates an approach that seems to have worked with Spring Boot versions 
before 3. But the autoconfiguration setup of Spring Boot 3.2.x. requires that a single `CqlSession` bean is defined - 
as the example code in the link instead creates multiple `CqlSession` beans, our application will not start properly.

[Other solutions](https://www.codingame.com/playgrounds/13689/multiple-keyspaces-using-a-single-spring-data-cassandratemplate) build upon avoiding the use of Spring Data `CrudRepositories`, and specifying the keyspace dynamically when 
writing the query. However, we found that these were not ideal as we wished to take advantage of automatic query generation, 
since we only needed pre-implemented queries, and we did not want the maintenance effort of writing queries manually.


## Implementing configurations for using multiple keyspaces in a single application

The code for the example can be found [Github](TODO).
### Defining our entities and repositories

To make future extensions easier to utilise, we have separated put each entity and its respective repositories into packages.
Alternatively, if they were all grouped in the same package, but this would require us to specify each entity and repository, rather than
simply giving the package name - which would mean that if we wanted to extend the schema with another table, we would need
to modify the configuration for sure. In the example, we set up subpackages for each keyspace (`keyspace1` and `keyspace2`), 
where we create the entities and repositories for the respective keyspace.

```java
@Table("A")
public record A(
        @PrimaryKeyColumn(name = "a", type = PrimaryKeyType.PARTITIONED) UUID a,
        @PrimaryKeyColumn(name = "b", type = PrimaryKeyType.CLUSTERED) String b,
        @Column("c") String c
) { }
```

### Disabling Spring Boot Data Cassandra autoconfigurations

When defining more than one `CqlSession` bean, the Spring Boot application will no longer start.
This is due to the `CassandraDataAutoConfiguration` and `CassandraReactiveDataAutoConfiguration`, which define a lot of 
typically useful beans using `@ConditionalOnMissingBean` but depend on a single `CqlSession` bean in the constructor. 
Instead, we need multiple `CqlSession` beans, one per keyspace we want to use. The solution to make this work is to exclude these autoconfiguration 
classes, as we don't need them anyway - note that this step might not be necessary in future Spring Boot versions. 

```java
@SpringBootApplication(exclude = {CassandraDataAutoConfiguration.class, CassandraReactiveDataAutoConfiguration.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
``` 

### Defining a configuration for our first keyspace

We already know we need two keyspaces, and so this is accounted for in the way we set up our first configuration. 
The `@EnableCassandraRepositories` annotation is made on our configuration, instead of using it on the `Application` class itself,
to emphasise that this configuration is used for one of our keyspace packages. We specify which `CassandraOperations` bean
to use - in the example, `aCassandraTemplate` - and we specify the packages for which to enable this configuration - in 
the example, the `keyspace1` package. This ensures that all `CrudRespository` instances within the provided package are correctly
instantiated.

```java
@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
public class Keyspace1Configuration {}
```

We inject all the necessary configuration parameters from our properties using the `@Value` annotations. In this case,
we cannot use the `spring` properties directly, so we qualify them using our keyspace name as a prefix. In our
case, we want to automatically create the keyspaces in the tests, so we also inject the schema action - which, for production,
should definitely be set to `NONE`.

```java
// inject all the necessary configuration parameters from our properties
@Value("${a.cassandra.contact-points}")
private String contactPoints;
@Value("${a.cassandra.local-datacenter}")
private String localDataCenter;
@Value("${a.cassandra.username}")
private String username;
@Value("${a.cassandra.password}")
private String password;
@Value("${a.cassandra.schema-action}")
private String schemaAction;
@Value("${a.keyspace-name}")
private String keySpaceName;
```

We need to create all the beans that Spring requires to use the repositories. In addition
to the actual `CqlSession`, this also includes configurations around the entities that represent the tables how to map
data from the database to the Java dcode. In our example, we also automatically create the keyspace - 
which we do for simplicity in testing, though we stress that this should not be carried out in a production environment. 
Additionally, we should note that there may be other configurations within the `SessionBuilderConfigurer` necessary to get a successful connection - but that's not topic of 
this post.

We name every single bean, which is necessary as we then create a second configuration with the same beans afterwards for the 
second keyspace. The full configuration is shown below and in the example code. Running the service with a single keyspace 
and this configuration will work without any issues. But we do not want to stop there.

```java
@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
public class Keyspace2Configuration {

    public static final String PACKAGE_NAME = "at.willhaben.springboot2keyspaces.keyspace1";

    @Value("${a.cassandra.contact-points}")
    private String contactPoints;

    @Value("${a.cassandra.local-datacenter}")
    private String localDataCenter;

    @Value("${a.cassandra.username}")
    private String username;

    @Value("${a.cassandra.password}")
    private String password;

    @Value("${a.cassandra.schema-action}")
    private String schemaAction;

    @Value("${a.keyspace-name}")
    private String keySpaceName;
    
    @Bean("aSessionBuilderConfigurer")
    public SessionBuilderConfigurer sessionBuilderConfigurer() {
        return sessionBuilder -> sessionBuilder.withAuthCredentials(username, password);
    }

    @Bean("aSession")
    public CqlSessionFactoryBean session(@Qualifier("aSessionBuilderConfigurer") SessionBuilderConfigurer sessionBuilderConfigurer) {
        CqlSessionFactoryBean session = new CqlSessionFactoryBean();
        session.setContactPoints(getContactPoints());
        session.setLocalDatacenter(getLocalDataCenter());
        session.setKeyspaceName(keySpaceName);
        session.setSessionBuilderConfigurer(sessionBuilderConfigurer);
        
        // The keyspace creations should only be done automatically in the tests
        CreateKeyspaceSpecification specification = CreateKeyspaceSpecification.createKeyspace(keySpaceName)
                .ifNotExists()
                .with(KeyspaceOption.DURABLE_WRITES, true)
                .withSimpleReplication();
        session.setKeyspaceCreations(List.of(specification));
        return session;
    }

    @Bean("aSessionFactory")
    public SessionFactoryFactoryBean sessionFactory(@Qualifier("aSession") CqlSession session, @Qualifier("aConverter") CassandraConverter converter) {
        SessionFactoryFactoryBean sessionFactory = new SessionFactoryFactoryBean();
        sessionFactory.setSession(session);
        sessionFactory.setConverter(converter);
        sessionFactory.setSchemaAction(getSchemaAction());
        return sessionFactory;
    }

    @Bean("aMappingContext")
    public CassandraMappingContext mappingContext() throws ClassNotFoundException {
        var context = new CassandraMappingContext();
        context.setManagedTypes(CassandraManagedTypes.fromIterable(CassandraEntityClassScanner.scan(PACKAGE_NAME)));

        return context;
    }

    @Bean("aConverter")
    public CassandraConverter converter(@Qualifier("aSession") CqlSession session, @Qualifier("aMappingContext") CassandraMappingContext mappingContext) {
        MappingCassandraConverter cassandraConverter = new MappingCassandraConverter(mappingContext);
        cassandraConverter.setUserTypeResolver(new SimpleUserTypeResolver(session));

        return cassandraConverter;
    }

    @Bean("aCassandraTemplate")
    public CassandraOperations cassandraTemplate(@Qualifier("aSessionFactory") SessionFactory sessionFactory, @Qualifier("aConverter") CassandraConverter converter) {
        return new CassandraTemplate(sessionFactory, converter);
    }
    
    private SchemaAction getSchemaAction() {
        return SchemaAction.valueOf(schemaAction);
    }
}
```

### Defining a configuration for our second keyspace

Defining the configuration for `keyspace2` is quite easy. One straightforward option is simply to copy the existing configuration, 
replace all `a` prefixes with `b` prefixes and voilà - you're done. But we don't like code duplication, and another approach
can make configuration changes easier. Our suggestions is to create a dedicated property source for the properties that stay the same.

```java
@Component
@ConfigurationProperties(prefix = "cassandra")
@Data
public class KeyspaceProperties {

    private String contactPoints;

    private String localDataCenter;

    private String username;

    private String password;

    private String schemaAction;
}
```  

Creating a factory in which to build the beans also makes the configuration easier to set up as it becomes 
a lot shorter (check out the example code to see how the factory is defined).

```java
@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "bCassandraTemplate",
        basePackages = Keyspace2Configuration.PACKAGE_NAME
)
@RequiredArgsConstructor
public class Keyspace2Configuration {

    public static final String PACKAGE_NAME = "at.willhaben.springboot2keyspaces.keyspace2";

    @Value("${b.keyspace-name}")
    private String keySpaceName;

    private final KeyspaceProperties keyspaceProperties;


    @Bean("bSessionBuilderConfigurer")
    public SessionBuilderConfigurer sessionBuilderConfigurer() {
        return KeyspaceServiceFactory
                .sessionBuilderConfigurer(keyspaceProperties.getUsername(),
                        keyspaceProperties.getPassword());
    }

    @Bean("bSession")
    public CqlSessionFactoryBean session(@Qualifier("bSessionBuilderConfigurer") SessionBuilderConfigurer sessionBuilderConfigurer) {
        return KeyspaceServiceFactory.session(sessionBuilderConfigurer, keyspaceProperties.getContactPoints(), keyspaceProperties.getLocalDataCenter(), keySpaceName);
    }

    @Bean("bSessionFactory")
    public SessionFactoryFactoryBean sessionFactory(@Qualifier("bSession") CqlSession session, @Qualifier("bConverter") CassandraConverter converter) {
        return KeyspaceServiceFactory.sessionFactory(session, converter, keyspaceProperties.getSchemaAction());
    }

    @Bean("bMappingContext")
    public CassandraMappingContext mappingContext() throws ClassNotFoundException {
        return KeyspaceServiceFactory.mappingContext(PACKAGE_NAME);
    }

    @Bean("bConverter")
    public CassandraConverter converter(@Qualifier("bSession") CqlSession session,
                                        @Qualifier("bMappingContext") CassandraMappingContext mappingContext) {
        return KeyspaceServiceFactory.converter(session, mappingContext);
    }

    @Bean("bCassandraTemplate")
    public CassandraOperations cassandraTemplate(@Qualifier("bSessionFactory") SessionFactory sessionFactory,
                                                 @Qualifier("bConverter") CassandraConverter converter) {
        return new CassandraTemplate(sessionFactory, converter);
    }
}
```

### Testing our configurations

Now we have our two configurations and we want to test them. To do so, we use the `Testcontainers` framework. We start a 
`Cassandra` container to use temporarily for our tests. In our configuration, we also create the keyspaces automatically - this can and should be done in other
ways but for the purpose of this example we take some shortcuts. Additionally, in this setup we configure the parameters for Spring to connect to Cassandra. 
If we inspect the Spring Data logs we can see that our queries are executed against
the correct keyspaces - likewise, in a deployed environment, you should see the data going to the right keyspaces. 

```java
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
                .containsExactly(b);
        assertThat(allA)
                .containsExactly(a);
    }
}
```

## Taking it one step further

The above examples use two separate tables and two separate keyspaces - matching the challenge we faced.
But out of curiosity, we wanted to see if a multi-tenancy-like approach could be implemented in this way - so
the same entity could definition be used for both keyspaces. To test this out, we define a third entity `C`. Of course, we could duplicate 
the entity per keyspace package, but this would require the rest of our system to know that this entity exists in 
separate sources. To achieve that, we'd have to rely on inheritance, or we would need to implement duplicate mapping logic to a global DTO, 
and we would risk ending up with out-of-sync entity definitions.

```java
@Table("C")
public record C(@PrimaryKeyColumn(name = "a", type = PrimaryKeyType.PARTITIONED) UUID a,
                @PrimaryKeyColumn(name = "b", type = PrimaryKeyType.CLUSTERED) String b,
                @Column("c") String c) {}
```

We also need a repository to access the data:
```java
@NoRepositoryBean
public interface CRepository extends MapIdCassandraRepository<C> {
    @Query
    C findByA(UUID a);
}
``` 

But how do we reference this single repository in two keyspaces? Here, we hit the limits of our current solution without requiring 
code duplication, as each repository can only use a single `CassandraTemplate`. This means we need to provide separate repositories. 
We can prefix them with either the keyspace name if it's very clear the keyspace name stays static, or we must somehow signify 
why there are two keyspaces (e.g. `Legacy`, `Eu/Us`, etc.). By using a common interface, which is implemented by the keyspace-specific repository definitions,
to define the queries, we can avoid further duplication. 

```java
public interface Keyspace2CRepository extends CRepository {
}
```

To enable this table to be used in our keyspaces we need to scan the package of the new table from both of our 
configurations.

```java
@Bean("bMappingContext")
public CassandraMappingContext mappingContext() throws ClassNotFoundException {
    return KeyspaceServiceFactory.mappingContext(PACKAGE_NAME, C.PACKAGE_NAME);
}
```

If we test our solution, we find that we can use the same partition and clustering key but set our values differently, 
and as long as we write in separate keyspaces, our entries remain. We would still have to implement the actual logic of our multi-tenancy approach 
- but this is up to you and your specific use case. 

```java
@Test
void given2ValuesWithSameId_whenPersistingValueInBothKeyspaces_thenValueBeReadById() {
    // Given
    UUID commonId = UUID.randomUUID();
    C c1 = new C(commonId, "test1", "test1");
    // Using the same id but different, still both should be readable from the table
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
```

## Conclusion

In sum, a setup of two or more separate Cassandra keyspaces within the same Spring Boot application, achieved by using 
Spring Data Cassandra requires quite a bit of configuration but is totally possible. The following steps should be taken:

* Disable the autoconfiguration classes provided by Spring, so we can define multiple `CqlSession` beans
* Introduce one configuration per keyspace, in order to separate the access by using separate beans
* Specify which entity classes and repositories are relevant for the current keyspace 

We hope that future versions of Spring Data Cassandra will support this setup with less need for custom configuration.  