package at.willhaben.springboot2keyspaces;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.experimental.UtilityClass;
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

import java.util.List;

@UtilityClass
public class KeyspaceServiceFactory {

    public static SessionBuilderConfigurer sessionBuilderConfigurer(String username, String password) {
        return sessionBuilder -> sessionBuilder.withAuthCredentials(username, password);
    }

    public static CqlSessionFactoryBean session(SessionBuilderConfigurer sessionBuilderConfigurer, String contactPoints, String localDataCenter, String keySpaceName) {
        CqlSessionFactoryBean session = new CqlSessionFactoryBean();
        session.setContactPoints(contactPoints);
        session.setLocalDatacenter(localDataCenter);
        session.setKeyspaceName(keySpaceName);
        session.setSessionBuilderConfigurer(sessionBuilderConfigurer);
        // The keyspace creations sould only be done automatically in the tests
        CreateKeyspaceSpecification specification = CreateKeyspaceSpecification.createKeyspace(keySpaceName)
                .ifNotExists()
                .with(KeyspaceOption.DURABLE_WRITES, true)
                .withSimpleReplication();
        session.setKeyspaceCreations(List.of(specification));

        return session;
    }

    public static SessionFactoryFactoryBean sessionFactory(CqlSession session, CassandraConverter converter, String schemaAction) {
        SessionFactoryFactoryBean sessionFactory = new SessionFactoryFactoryBean();
        sessionFactory.setSession(session);
        sessionFactory.setConverter(converter);
        sessionFactory.setSchemaAction(getSchemaAction(schemaAction));

        return sessionFactory;
    }

    public static CassandraMappingContext mappingContext(String... packageNames) throws ClassNotFoundException {
        var context = new CassandraMappingContext();
        context.setManagedTypes(CassandraManagedTypes.fromIterable(CassandraEntityClassScanner.scan(packageNames)));

        return context;
    }

    public static CassandraConverter converter(CqlSession session, CassandraMappingContext mappingContext) {
        MappingCassandraConverter cassandraConverter = new MappingCassandraConverter(mappingContext);
        cassandraConverter.setUserTypeResolver(new SimpleUserTypeResolver(session));

        return cassandraConverter;
    }

    public static CassandraOperations cassandraTemplate(SessionFactory sessionFactory, CassandraConverter converter) {
        return new CassandraTemplate(sessionFactory, converter);
    }

    private static SchemaAction getSchemaAction(String schemaAction) {
        return SchemaAction.valueOf(schemaAction);
    }
}
