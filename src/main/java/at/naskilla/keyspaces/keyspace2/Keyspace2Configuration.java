package at.naskilla.keyspaces.keyspace2;

import at.naskilla.keyspaces.KeyspaceProperties;
import at.naskilla.keyspaces.KeyspaceServiceFactory;
import com.datastax.oss.driver.api.core.CqlSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
        cassandraTemplateRef = "bCassandraTemplate",
        basePackages = Keyspace2Configuration.PACKAGE_NAME
)
@RequiredArgsConstructor
public class Keyspace2Configuration {

    public static final String PACKAGE_NAME = "at.naskilla.keyspaces.keyspace2";

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
