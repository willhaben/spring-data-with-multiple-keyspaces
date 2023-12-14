package at.naskilla.keyspaces;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cassandra")
@Data
public class KeyspaceProperties {

    private String contactPoints;

    private String localDataCenter;

    private String username;

    private String password;

    private String schemaAction;
}
