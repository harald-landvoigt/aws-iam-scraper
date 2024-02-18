package at.landvoigt.tools.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@ApplicationScoped
public class CredentialProviderFactory {
    @Inject
    HttpClientFactory httpClientFactory;

    public AwsCredentialsProvider getCredentials(final String roleArn) {

        final StsClient stsClient = StsClient.builder()
                .httpClient(httpClientFactory.create())
                .build();

        final StsAssumeRoleCredentialsProvider stsAssumeRoleCredentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .stsClient(stsClient)
                .refreshRequest(AssumeRoleRequest.builder()
                        .roleArn(roleArn)
                        .roleSessionName("AwsIamScraper")
                        .build())
                .build();

        return stsAssumeRoleCredentialsProvider;
    }

}
