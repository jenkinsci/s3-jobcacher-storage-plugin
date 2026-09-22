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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import software.amazon.awssdk.services.s3.internal.Mimetypes;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * From S3 Plugin modified for this purpose
 *
 * This class supports subclasses to create S3 object metadata
 *
 * @param <T>
 */
public abstract class S3BaseUploadCallable<T> extends S3Callable<T> {

    private static final long serialVersionUID = 1L;

    private final String storageClass;
    private final Map<String, String> userMetadata;
    private final boolean useServerSideEncryption;

    public S3BaseUploadCallable(
            ClientHelper clientHelper,
            Map<String, String> userMetadata,
            String storageClass,
            boolean useServerSideEncryption) {
        super(clientHelper);
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
        this.useServerSideEncryption = useServerSideEncryption;
    }

    protected HeadObjectResponse buildMetadata(File file) throws IOException {
        HeadObjectResponse metadata = HeadObjectResponse.builder()
                .build();
        metadata.contentType(Mimetypes.getInstance().getMimetype(file.getName()));
        metadata.contentLength(file.length());
        metadata.lastModified(new Date(file.lastModified()));

        if (storageClass != null && !storageClass.isEmpty()) {
            metadata.header("x-amz-storage-class", storageClass);
        }

        if (useServerSideEncryption) {
            metadata.sseAlgorithm(HeadObjectResponse.AES_256_SERVER_SIDE_ENCRYPTION);
        }

        for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
            String key = entry.getKey().toLowerCase();
            switch (key) {
                case "cache-control":
                    metadata.cacheControl(entry.getValue());
                    break;
                case "expires":
                    try {
                        Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(entry.getValue());
                        metadata.httpExpiresDate(expires);
                    } catch (ParseException e) {
                        metadata.addUserMetadata(entry.getKey(), entry.getValue());
                    }
                    break;
                case "content-encoding":
                    metadata.contentEncoding(entry.getValue());
                    break;
                case "content-type":
                    metadata.contentType(entry.getValue());
                default:
                    metadata.addUserMetadata(entry.getKey(), entry.getValue());
                    break;
            }
        }
        return metadata;
    }
}
