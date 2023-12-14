package at.willhaben.springboot2keyspaces.keyspace1;

import org.springframework.data.cassandra.repository.MapIdCassandraRepository;

public interface ARepository extends MapIdCassandraRepository<A> {

}
