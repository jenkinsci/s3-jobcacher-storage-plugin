package s3mock;

import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class S3MockContainer extends GenericContainer<S3MockContainer> {

    public S3MockContainer() {
        this("rustfs/rustfs:1.0.0");
    }

    public S3MockContainer(String dockerImageName) {
        super(dockerImageName);

        setWaitStrategy(Wait.forListeningPort());

        withEnv("RUSTFS_ACCESS_KEY", UUID.randomUUID().toString());
        withEnv("RUSTFS_SECRET_KEY", UUID.randomUUID().toString());
        withExposedPorts(9000);
        // explicit network so S3AdminContainer can reach this container by network alias
        withNetwork(Network.newNetwork());
    }

    public String accessKey() {
        return getEnvMap().get("RUSTFS_ACCESS_KEY");
    }

    public String secretKey() {
        return getEnvMap().get("RUSTFS_SECRET_KEY");
    }

    public String getExternalAddress() {
        return "http://localhost:" + getMappedPort(9000);
    }
}
