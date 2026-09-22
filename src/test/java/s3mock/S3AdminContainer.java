package s3mock;

import static java.lang.String.format;

import java.io.IOException;
import org.testcontainers.containers.GenericContainer;

public class S3AdminContainer extends GenericContainer<S3AdminContainer> {

    private final S3MockContainer s3;

    public S3AdminContainer(S3MockContainer s3) {
        super("amazon/aws-cli:2.36.46");
        this.s3 = s3;
        dependsOn(s3);
        withNetwork(s3.getNetwork());
        withCreateContainerCmdModifier(c -> c.withTty(true).withEntrypoint("/bin/sh"));
        withEnv("AWS_ACCESS_KEY_ID", s3.accessKey());
        withEnv("AWS_SECRET_ACCESS_KEY", s3.secretKey());
    }

    private String endpointUrl() {
        return format("http://%s:9000", s3.getNetworkAliases().get(0));
    }

    public ExecResult execSecure(String command, Object... args) throws IOException, InterruptedException {
        ExecResult result = exec(command, args);
        if (result.getExitCode() != 0) {
            throw new AssertionError(result.getStderr());
        }
        return result;
    }

    public ExecResult exec(String command, Object... args) throws IOException, InterruptedException {
        return execInContainer("/bin/sh", "-c", format(command, args));
    }

    public void deleteBucket(String bucket) throws IOException, InterruptedException {
        exec("aws --endpoint-url %s s3 rb s3://%s --force", endpointUrl(), bucket);
    }

    public void createBucket(String bucket) throws IOException, InterruptedException {
        execSecure("aws --endpoint-url %s s3 mb s3://%s", endpointUrl(), bucket);
    }

    public void createObject(String bucket, String key, String content) throws IOException, InterruptedException {
        execSecure("echo -n \"%s\" | aws --endpoint-url %s s3 cp - s3://%s/%s", content, endpointUrl(), bucket, key);
    }
}
