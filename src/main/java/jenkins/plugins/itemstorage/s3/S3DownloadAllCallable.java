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

import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import java.io.File;
import java.io.IOException;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3ObjectSummary;

/**
 * Copies all objects from the path in S3 to the target base path
 *
 * @author Peter Hayes
 */
public class S3DownloadAllCallable extends S3Callable<Integer> {

    private static final long serialVersionUID = 1L;

    private final String bucketName;
    private final String pathPrefix;
    private final DirScanner.Glob scanner;

    public S3DownloadAllCallable(
            ClientHelper helper,
            String fileMask,
            String excludes,
            boolean useDefaultExcludes,
            String bucketName,
            String pathPrefix) {
        super(helper);
        this.bucketName = bucketName;
        this.pathPrefix = pathPrefix;

        scanner = new DirScanner.Glob(fileMask, excludes, useDefaultExcludes);
    }

    /**
     * Download to executor
     */
    @Override
    public Integer invoke(TransferManager transferManager, File base, VirtualChannel channel)
            throws IOException, InterruptedException {
        if (!base.exists()) {
            if (!base.mkdirs()) {
                throw new IOException("Failed to create directory : " + base);
            }
        }

        int totalCount;
        Downloads downloads = new Downloads();
        ListObjectsResponse objectListing = null;

        do {
            objectListing = transferManager
                    .getAmazonS3Client()
                    .listObjects(ListObjectsRequest.builder()
                    .bucket(bucketName)
                    .prefix(pathPrefix)
                    .marker(objectListing != null ? objectListing.nextMarker() : null)
                    .build());

            for (S3ObjectSummary summary : objectListing.objectSummaries()) {
                downloads.startDownload(transferManager, base, pathPrefix, summary);
            }
        } while (objectListing.nextMarker() != null);

        // Grab # of files copied
        totalCount = downloads.count();

        // Finish the asynchronous downloading process
        downloads.finishDownloading();

        return totalCount;
    }
}
