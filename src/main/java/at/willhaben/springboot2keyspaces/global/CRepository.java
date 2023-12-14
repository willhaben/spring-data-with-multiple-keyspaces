package at.willhaben.springboot2keyspaces.global;

import org.springframework.data.cassandra.repository.MapIdCassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface CRepository extends MapIdCassandraRepository<C> {

    @Query
    C findByA(UUID a);
}
