package jenkins.plugins.itemstorage.s3;

import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder.EndpointConfiguration;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.Serializable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Modification of the Jenkins S3 Plugin
 *
 * Stores settings to be used at a later time.
 */
public class ClientHelper implements Serializable {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("lgtm[jenkins/plaintext-storage]") // Never saved on disk
    private final String accessKey;

    @SuppressWarnings("lgtm[jenkins/plaintext-storage]") // Never saved on disk
    private final String secretKey;

    private final String region;
    private final ProxyConfiguration proxy;
    private final String endpoint;
    private final String signerVersion;
    private final boolean pathStyleAccess;
    private final boolean parallelDownloads;

    private transient AwsCredentials credentials;
    private transient S3Client client;

    public ClientHelper(AwsCredentials credentials, ProxyConfiguration proxy) {
        this(credentials, null, proxy);
    }

    public ClientHelper(AwsCredentials credentials, String region, ProxyConfiguration proxy) {
        this(credentials, null, region, proxy, null, false, true);
    }

    public ClientHelper(
            AwsCredentials credentials,
            String endpoint,
            String region,
            ProxyConfiguration proxy,
            String signerVersion,
            boolean pathStyleAccess,
            boolean parallelDownloads) {
        this.region = region;
        this.proxy = proxy;
        this.endpoint = endpoint;
        this.signerVersion = signerVersion;
        this.pathStyleAccess = pathStyleAccess;
        this.parallelDownloads = parallelDownloads;

        if (credentials != null) {
            this.accessKey = credentials.accessKeyId();
            this.secretKey = credentials.secretAccessKey();
        } else {
            this.accessKey = null;
            this.secretKey = null;
        }
    }

    public boolean supportsParallelDownloads() {
        return parallelDownloads;
    }

    public synchronized S3Client client() {
        if (client == null) {
            S3ClientBuilder builder = S3Client.builder();
            ClientOverrideConfiguration config = getClientConfiguration(proxy);

            if (getCredentials() != null) {
                builder.credentialsProvider(StaticCredentialsProvider.create(getCredentials()));
            }

            if (endpoint != null) {
                builder.endpointOverride(new EndpointConfiguration(endpoint, region));
                builder.pathStyleAccessEnabled(pathStyleAccess);
                config.signerOverride(signerVersion);
            } else if (region != null) {
                builder.region(Region.of(region));
            }

            builder.httpClientBuilder(ApacheHttpClient.builder()).overrideConfiguration(config);
            client = builder.build();
        }

        return client;
    }

    public static ClientOverrideConfiguration getClientConfiguration(ProxyConfiguration proxy) {
        ClientOverrideConfiguration clientConfiguration = ClientOverrideConfiguration.builder()
                .build();

        if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
            clientConfiguration.proxyHost(proxy.name);
            clientConfiguration.proxyPort(proxy.port);

            if (proxy.getUserName() != null) {
                clientConfiguration.proxyUsername(proxy.getUserName());
                clientConfiguration.proxyPassword(Secret.toString(proxy.getSecretPassword()));
            }
        }

        return clientConfiguration;
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if (proxy == null) {
            return false;
        }

        return proxy.getNoProxyHostPatterns().stream()
                .noneMatch(p -> p.matcher(hostname).matches());
    }

    public synchronized AwsCredentials getCredentials() {
        if (credentials == null && accessKey != null && secretKey != null) {
            credentials = AwsBasicCredentials.create(accessKey, secretKey);
        }
        return credentials;
    }
}
