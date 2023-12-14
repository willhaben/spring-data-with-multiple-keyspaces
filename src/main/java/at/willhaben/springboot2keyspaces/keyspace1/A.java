package at.willhaben.springboot2keyspaces.keyspace1;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Table("A")
public record A(
        @PrimaryKeyColumn(name = "a", type = PrimaryKeyType.PARTITIONED) UUID a,
        @PrimaryKeyColumn(name = "b", type = PrimaryKeyType.CLUSTERED) String b,
        @Column("c") String c
) {
}
