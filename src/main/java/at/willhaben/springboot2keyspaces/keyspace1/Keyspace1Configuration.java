package at.willhaben.springboot2keyspaces.keyspace1;

import at.willhaben.springboot2keyspaces.KeyspaceProperties;
import at.willhaben.springboot2keyspaces.KeyspaceServiceFactory;
import at.willhaben.springboot2keyspaces.global.C;
import com.datastax.oss.driver.api.core.CqlSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.SessionFactory;
import org.springframework.data.cassandra.config.*;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

@Configuration
@EnableCassandraRepositories(
        cassandraTemplateRef = "aCassandraTemplate",
        basePackages = Keyspace1Configuration.PACKAGE_NAME
)
@RequiredArgsConstructor
public class Keyspace1Configuration {

    public static final String PACKAGE_NAME = "at.willhaben.springboot2keyspaces.keyspace1";

    @Value("${a.keyspace-name}")
    private String keySpaceName;

    private final KeyspaceProperties keyspaceProperties;


    @Bean("aSessionBuilderConfigurer")
    public SessionBuilderConfigurer sessionBuilderConfigurer() {
        return KeyspaceServiceFactory
                .sessionBuilderConfigurer(keyspaceProperties.getUsername(),
                        keyspaceProperties.getPassword());
    }

    @Bean("aSession")
    public CqlSessionFactoryBean session(@Qualifier("aSessionBuilderConfigurer") SessionBuilderConfigurer sessionBuilderConfigurer) {
        return KeyspaceServiceFactory.session(sessionBuilderConfigurer, keyspaceProperties.getContactPoints(), keyspaceProperties.getLocalDataCenter(), keySpaceName);
    }

    @Bean("aSessionFactory")
    public SessionFactoryFactoryBean sessionFactory(@Qualifier("aSession") CqlSession session, @Qualifier("aConverter") CassandraConverter converter) {
        return KeyspaceServiceFactory.sessionFactory(session, converter, keyspaceProperties.getSchemaAction());
    }

    @Bean("aMappingContext")
    public CassandraMappingContext mappingContext() throws ClassNotFoundException {
        return KeyspaceServiceFactory.mappingContext(PACKAGE_NAME, C.PACKAGE_NAME);
    }

    @Bean("aConverter")
    public CassandraConverter converter(@Qualifier("aSession") CqlSession session,
                                        @Qualifier("aMappingContext") CassandraMappingContext mappingContext) {
        return KeyspaceServiceFactory.converter(session, mappingContext);
    }

    @Bean("aCassandraTemplate")
    public CassandraOperations cassandraTemplate(@Qualifier("aSessionFactory") SessionFactory sessionFactory,
                                                 @Qualifier("aConverter") CassandraConverter converter) {
        return new CassandraTemplate(sessionFactory, converter);
    }

}
