package plugins.jobcacher;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import java.util.UUID;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import s3mock.S3AdminContainer;
import s3mock.S3MockContainer;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
class ArbitraryFileCacheStepS3MockTest {

    @Container
    private static final S3MockContainer s3 = new S3MockContainer();

    @Container
    private static final S3AdminContainer s3Admin = new S3AdminContainer(s3);

    private static void setupCache(JenkinsRule j) throws Exception {
        // create a test bucket in the S3-compatible mock
        String bucket = UUID.randomUUID().toString();
        s3Admin.createBucket(bucket);

        // setup credentials for bucket in Jenkins
        AWSCredentialsImpl credentials = new AWSCredentialsImpl(
                CredentialsScope.SYSTEM,
                "s3-test-credentials-id",
                s3.accessKey(),
                s3.secretKey(),
                "s3 test credentials");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();

        // configure the corresponding ItemStorage in Jenkins
        NonAWSS3ItemStorage storage = new NonAWSS3ItemStorage(
                "s3-test-credentials-id",
                bucket,
                "instances1/",
                s3.getExternalAddress(),
                "us-west-1",
                null,
                true,
                false);
        GlobalItemStorage.get().setStorage(storage);
    }

    @Test
    void testBackupAndRestore(JenkinsRule j) throws Exception {

        setupCache(j);

        j.jenkins.setNumExecutors(0);
        j.createSlave(true);

        // GIVEN
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  cache(maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){
                    sh 'mkdir sub'
                    sh 'echo sub-content > sub/file'
                  }
                }""", true));

        // WHEN
        WorkflowRun result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Skip restoring cache as no up-to-date cache exists", result);
        j.assertLogContains("Creating cache...", result);

        // GIVEN
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  sh 'rm -rf *'
                  cache(skipSave: true, maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){
                    sh 'rm sub/file'
                  }
                }""", true));

        // WHEN
        result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Skipping save due to skipSave being set to true.", result);

        // GIVEN
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  sh 'rm -rf *'
                  cache(maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){
                    sh 'cat sub/file'
                  }
                }""", true));

        // WHEN
        result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("sub-content", result);
    }
}
