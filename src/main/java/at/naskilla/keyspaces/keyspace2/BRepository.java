package at.naskilla.keyspaces.keyspace2;

import at.naskilla.keyspaces.keyspace1.A;
import org.springframework.data.cassandra.repository.MapIdCassandraRepository;

public interface BRepository extends MapIdCassandraRepository<B> {
    
}
