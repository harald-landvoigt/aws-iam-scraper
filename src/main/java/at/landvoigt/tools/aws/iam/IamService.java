package at.landvoigt.tools.aws.iam;

import at.landvoigt.tools.aws.CredentialProviderFactory;
import at.landvoigt.tools.aws.HttpClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
@Slf4j
public class IamService {

    private final ExecutorService executor = Executors.newFixedThreadPool(100);
    @Inject
    private HttpClientFactory httpClientFactory;
    @Inject
    private CredentialProviderFactory credentialProviderFactory;


    @ConfigProperty(name = "aws.account.role.iam.reader.arn")
    String accountIamReaderRole;

    public Collection<AccessKeyData> findAccessKeyData(final Collection<String> accountIds) {
        final Collection<AccessKeyData> result = new ArrayList<>();
        final Collection<CompletableFuture<Collection<AccessKeyData>>> futures = new ArrayList<>();
        accountIds.forEach(accountId -> futures.add(CompletableFuture.supplyAsync(() -> findAccessKeyData(accountId), executor)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        futures.forEach(future -> result.addAll(future.join()));
        return result;
    }

    private Collection<AccessKeyData> findAccessKeyData(final String accountId) {
        log.info("scrape iam data in account {}", accountId);
        final Collection<AccessKeyData> result = new ArrayList<>();
        try (final IamClient iamClient = createClient(accountIamReaderRole.replace("ACCOUNT", accountId))) {
            String marker = null;
            do {
                final ListUsersResponse listUserResponse = iamClient.listUsers(ListUsersRequest.builder()
                        .marker(marker)
                        .maxItems(50)
                        .build());
                marker = listUserResponse.marker();
                for (final User user : listUserResponse.users()) {
                    result.addAll(findAccessKeysForUser(user, accountId, iamClient));
                }
            } while (marker != null);
        } catch (final Exception e) {
            log.warn("error accessing account {} with {}", accountId, e.toString());
        }
        log.info("finished scrape iam data in account {}", accountId);
        return result;
    }

    private Collection<AccessKeyData> findAccessKeysForUser(final User user, final String accountId, final IamClient iamClient) {
        final Collection<AccessKeyData> result = new ArrayList<>();
        final ListAccessKeysResponse listAccessKeysResponse = iamClient.listAccessKeys(ListAccessKeysRequest.builder().userName(user.userName()).build());
        listAccessKeysResponse.accessKeyMetadata()
                .forEach(key -> result.add(
                        AccessKeyData.builder()
                                .accountId(accountId)
                                .accessKeyId(key.accessKeyId())
                                .userName(user.userName())
                                .status(key.statusAsString())
                                .creationDate(key.createDate().toString())
                                .build()));


        return result;
    }


    private IamClient createClient(final String accountIamReaderRole) {
        return IamClient.builder()
                .httpClient(httpClientFactory.create())
                .credentialsProvider(credentialProviderFactory.getCredentials(accountIamReaderRole))
                .region(Region.US_EAST_1)
                .build();
    }
}
