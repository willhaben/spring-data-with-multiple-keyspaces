package at.naskilla.keyspaces.keyspace2;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Table("A")
public record B(
        @PrimaryKeyColumn(name = "a", type = PrimaryKeyType.PARTITIONED) UUID x,
        @PrimaryKeyColumn(name = "b", type = PrimaryKeyType.CLUSTERED) String y,
        @Column("c") String z
) {
}
