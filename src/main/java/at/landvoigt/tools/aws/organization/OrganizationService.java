package at.landvoigt.tools.aws.organization;

import at.landvoigt.tools.aws.CredentialProviderFactory;
import at.landvoigt.tools.aws.HttpClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class OrganizationService {
    @Inject
    private HttpClientFactory httpClientFactory;
    @Inject
    private CredentialProviderFactory credentialProviderFactory;

    @Getter
    private final String organizationReaderRoleArn;

    public OrganizationService(@ConfigProperty(name = "aws.organization.role.reader.arn") final String roleArn,
                               @ConfigProperty(name = "aws.organization.account") final String organizationAccountId) {

        if (organizationAccountId == null || !Pattern.compile("[0-9]{12}").matcher(organizationAccountId).matches()) {
            throw new IllegalArgumentException("Invalid organization account id: " + organizationAccountId);
        }

        organizationReaderRoleArn = roleArn.replace("ORG_ACCOUNT_ID", organizationAccountId);
        log.info("Using role arn: {}", organizationReaderRoleArn);
    }

    public Collection<String> findAllAccounts() {
        final Collection<String> accountIds = new ArrayList<>();
        String token = null;
        try (final OrganizationsClient organizationsClient = createClient(organizationReaderRoleArn)) {
            do {
                final ListAccountsResponse listAccessKeysResponse = organizationsClient.listAccounts(
                        ListAccountsRequest.builder()
                                .nextToken(token)
                                .build());
                accountIds.addAll(listAccessKeysResponse.accounts().stream().map(Account::id).toList());
                token = listAccessKeysResponse.nextToken();
            } while (token != null);
        }
        return accountIds;
    }

    private OrganizationsClient createClient(final String organizationReaderRoleArn) {
        return OrganizationsClient.builder()
                .httpClient(httpClientFactory.create())
                .credentialsProvider(credentialProviderFactory.getCredentials(organizationReaderRoleArn))
                .region(Region.US_EAST_1)
                .build();
    }
}
