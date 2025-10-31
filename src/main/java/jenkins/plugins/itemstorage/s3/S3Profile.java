/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.itemstorage.s3;

import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Based on same named class in S3 Jenkins Plugin
 * <p>
 * Reusable class for interacting with S3 for file operations
 *
 * @author Peter Hayes
 */
public class S3Profile {

    private final ClientHelper helper;
    private final String prefix;

    @DataBoundConstructor
    public S3Profile(
            AmazonWebServicesCredentials credentials,
            String endpoint,
            String region,
            String prefix,
            String signerVersion,
            boolean pathStyleAccess,
            boolean parallelDownloads) {
        this.helper = new ClientHelper(
                credentials != null ? credentials.resolveCredentials() : null,
                endpoint,
                region,
                getProxy(),
                signerVersion,
                pathStyleAccess,
                parallelDownloads);
        this.prefix = prefix;
    }

    public void upload(
            String bucketName,
            String path,
            FilePath source,
            Map<String, String> userMetadata,
            String storageClass,
            boolean useServerSideEncryption)
            throws IOException, InterruptedException {
        FilePath.FileCallable<Void> upload = new S3UploadCallable(
                helper, bucketName, withPrefix(path), userMetadata, storageClass, useServerSideEncryption);

        source.act(upload);
    }

    public int uploadAll(
            String bucketName,
            String path,
            String fileMask,
            String excludes,
            boolean useDefaultExcludes,
            FilePath source,
            Map<String, String> userMetadata,
            String storageClass,
            boolean useServerSideEncryption)
            throws IOException, InterruptedException {
        FilePath.FileCallable<Integer> upload = new S3UploadAllCallable(
                helper,
                fileMask,
                excludes,
                useDefaultExcludes,
                bucketName,
                withPrefix(path),
                userMetadata,
                storageClass,
                useServerSideEncryption);

        return source.act(upload);
    }

    public boolean exists(String bucketName, String path) {
        return helper.client().doesObjectExist(bucketName, withPrefix(path));
    }

    public void download(String bucketName, String key, FilePath target) throws IOException, InterruptedException {
        FilePath.FileCallable<Void> download = new S3DownloadCallable(helper, bucketName, withPrefix(key));

        target.act(download);
    }

    public int downloadAll(
            String bucketName,
            String pathPrefix,
            String fileMask,
            String excludes,
            boolean useDefaultExcludes,
            FilePath target)
            throws IOException, InterruptedException {
        FilePath.FileCallable<Integer> download = new S3DownloadAllCallable(
                helper, fileMask, excludes, useDefaultExcludes, bucketName, withPrefix(pathPrefix));

        return target.act(download);
    }

    public void delete(String bucketName, String pathPrefix) {
        ListObjectsResponse listing = null;
        do {
            listing = listing == null
                    ? helper.client().listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(withPrefix(pathPrefix))
                            .build())
                    : helper.client().listNextBatchOfObjects(listing);

            DeleteObjectsRequest req = DeleteObjectsRequest.builder().bucket(bucketName)
                    .build();

            List<DeleteObjectsRequest.KeyVersion> keys =
                    new ArrayList<>(listing.objectSummaries().size());
            for (S3ObjectSummary summary : listing.objectSummaries()) {
                keys.add(new software.amazon.awssdk.services.s3.model.DeleteObjectsRequestKeyVersion(summary.key()));
            }
            req.keys(keys);

            helper.client().deleteObjects(req);
        } while (listing.isTruncated());
    }

    public void rename(String bucketName, String currentPathPrefix, String newPathPrefix) {
        ListObjectsResponse listing = null;
        do {
            listing = listing == null
                    ? helper.client().listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(withPrefix(currentPathPrefix))
                            .build())
                    : helper.client().listNextBatchOfObjects(listing);
            for (S3ObjectSummary summary : listing.objectSummaries()) {
                String key = summary.key();

                helper.client()
                        .copyObject(CopyObjectRequest.builder().sourceBucket(bucketName).sourceKey(key).destinationBucket(bucketName).destinationKey(withPrefix(newPathPrefix)
                        + key.substring(
                        withPrefix(currentPathPrefix).length()))
                        .build());
                helper.client().deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key)
                        .build());
            }
        } while (listing.isTruncated());
    }

    private ProxyConfiguration getProxy() {
        return Jenkins.getActiveInstance().proxy;
    }

    private String withPrefix(String path) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return path;
        } else {
            return String.format("%s%s", prefix, path);
        }
    }
}
