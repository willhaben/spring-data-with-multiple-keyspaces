# Spring Boot Data Cassandra With Multiple Keyspaces

## Introduction
Disclaimer: Whether or not to use multiple keyspaces within a single application is a discussion for a different time, we
want to present the solution we managed to find for the problem at hand.

We use AWS Keyspaces for storing some of our data here at willhaben. In one of our services
we need to use data from multiple of the keyspaces defined.
While adding the connection to the second keyspace into our codebase we ran into an issue: Spring Boot Data Cassandra does not support using
multiple keyspaces out of the box.

While we were able to find some resources explaining solutions on how to get it working,
we were not able to set it up for various reasons. Within [this article](https://www.codingame.com/playgrounds/13647/multiple-keyspaces-with-spring-data-cassandra)
the appraoch seems to have worked with Spring Boot versions before 3.x.x, but with the new setup the autoconfiguration
of Spring required us to define a single `CqlSession` bean - which we needed more than 1 of for our keyspaces.

[Other solutions](https://www.codingame.com/playgrounds/13689/multiple-keyspaces-using-a-single-spring-data-cassandratemplate) build upon not to use Spring Data `CrudRepositories`
and to specify the keyspaces dynamically on the query. We did not really want to go this route to take advantage of the automatic
query generation since the actual queries were pretty straightforward in the end and we wanted to avoid the maintenance effort.

## Implementing configurations for using multiple keyspaces in a single application

### Define our 2 entities and repositories

To make future extensions easier, we should put each entity and the respective repositories into separate packages.
We can put all of them into the same package, but this will require us to exactly specify each entity and repository instead
being able to give the package name - which would mean if we would like to extend the schema with another table, we'd also need
to touch the configuration for sure. I would like to avoid this, so in the example we created the Tables and repositories in
their own subpackages `keyspace1` and `keyspace2`. The rest is up to your needs - for the example we did not speicfy any 
special queries or complex datastructures.

### Define a configuration for our first keyspace

We already know we need 2 keyspaces, so the way we set up our first configuration already takes this into consideration. 
The `@EnableCassandraRepositories` annotation is on our configuration instead of using it on the `Application` class itself
to emphasize that this configuration is used for one of our keyspace packages. We specify which  `CassandraOperations` bean
to use by giving it a qualified name - in the example `aCassandraTemplate` - and specify the packages to enable this configuration for - in 
the example the `keyspace1` package.

```java
@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
public class Keyspace1Configuration {}
```

We inject all the necessary configuration parameters from our properties using the `@Value` annotations. In this case
we cannot use the auto configuration `spring` properties, so we qualify them using our keyspace name as prefix. As in our
case we want to automatically create the keyspaces in the tests we also inject the schema action - which for production
should definitely be set to `NONE`.

```java
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

Now the fun part starts. We need to create all the beans Spring requires to use the repositories. This includes in addition
to the actual `CqlSession` also configurations on which entities are mapped within this configuration
and should be possible to be mapped by our ORM mapper. In our example below we also automatically create the keyspace - 
which we do for simplicity in testing but should never be done in a production environment. 

Also, we have to qualify every single bean, as we'll create a second configuration with the same beans afterward for the 
second keyspace. I've included the full configuration below. Running the service with a single keyspace and this configuration
will work without any issues. But we do not want to stop there.

```java
@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
public class Keyspace2Configuration {

    public static final String PACKAGE_NAME = "at.naskilla.keyspaces.keyspace1";

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

### Define a configuration for our second keyspace

### Disable Spring Boot Data Cassandra autoconfigurations

When defining more than 1 `CqlSession` bean your spring boot application will no longer start.
This is due to the `CassandraDataAutoConfiguration` and `CassandraReactiveDataAutoConfiguration`, which define a lot of 
typically useful beans using `@ConditionalOnMissingBean`. This would mean if we define them ourselfs - as we do for using
multiple keyspaces they are not initialised. But the constructors of the auto configurations expect a single `CqlSession`
as dependency, which results in even if not a single other bean is initialised by the configuration having 2 beans of
`CqlSession` will break the startup. The simple solution for this is to exclude these autoconfiguration classes as we don't
need them anyway. 

```java
@SpringBootApplication(exclude = {CassandraDataAutoConfiguration.class, CassandraReactiveDataAutoConfiguration.class})
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
}
``` 