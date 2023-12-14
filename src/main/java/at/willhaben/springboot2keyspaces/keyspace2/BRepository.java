package at.willhaben.springboot2keyspaces.keyspace2;

import org.springframework.data.cassandra.repository.MapIdCassandraRepository;

public interface BRepository extends MapIdCassandraRepository<B> {
    
}
