package at.naskilla.keyspaces.keyspace1;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.CassandraManagedTypes;
import org.springframework.data.cassandra.SessionFactory;
import org.springframework.data.cassandra.config.*;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.util.List;

@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
public class Keyspace1Configuration {

    public static final String PACKAGE_NAME = "at.naskilla.keyspaces.keyspace1";

    @Value("${cassandra.contact-points}")
    private String contactPoints;

    @Value("${cassandra.local-datacenter}")
    private String localDataCenter;

    @Value("${cassandra.username}")
    private String username;

    @Value("${cassandra.password}")
    private String password;

    @Value("${cassandra.schema-action}")
    private String schemaAction;

    @Value("${a.keyspace-name}")
    private String keySpaceName;


    @Bean("aSessionBuilderConfigurer")
    public SessionBuilderConfigurer sessionBuilderConfigurer() {
        return sessionBuilder -> sessionBuilder.withAuthCredentials(username, password);
    }

    public SchemaAction getSchemaAction() {
        return SchemaAction.valueOf(schemaAction);
    }

    @Bean("aSession")
    public CqlSessionFactoryBean session(@Qualifier("aSessionBuilderConfigurer") SessionBuilderConfigurer sessionBuilderConfigurer) {
        CqlSessionFactoryBean session = new CqlSessionFactoryBean();
        session.setContactPoints(contactPoints);
        session.setLocalDatacenter(localDataCenter);
        session.setKeyspaceName(keySpaceName);
        session.setSessionBuilderConfigurer(sessionBuilderConfigurer);
        // The keyspace creations sould only be done automatically in the tests
        var specification = CreateKeyspaceSpecification.createKeyspace(keySpaceName)
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

}
