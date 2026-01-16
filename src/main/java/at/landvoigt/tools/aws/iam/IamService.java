package at.landvoigt.tools.aws.iam;

import at.landvoigt.tools.aws.CredentialProviderFactory;
import at.landvoigt.tools.aws.HttpClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GenerateCredentialReportResponse;
import software.amazon.awssdk.services.iam.model.GetCredentialReportResponse;
import software.amazon.awssdk.services.iam.model.State;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class IamService {

    private static final int CREDENTIAL_REPORT_GENERATION_WAIT_SECONDS = 5;
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
        try (final IamClient iamClient = createClient(accountIamReaderRole.replace("ACCOUNT", accountId))) {
            final SdkBytes credentialReport = downloadCredentialReport(iamClient);
            return parseCredentialReport(credentialReport, accountId);
        } catch (final Exception e) {
            log.warn("error accessing account {} with {}", accountId, e.toString());
        }
        log.info("finished scrape iam data in account {}", accountId);
        return new ArrayList<>();
    }

    private SdkBytes downloadCredentialReport(final IamClient iamClient) throws InterruptedException {
        iamClient.generateCredentialReport();
        GetCredentialReportResponse response;
        do {
            TimeUnit.SECONDS.sleep(CREDENTIAL_REPORT_GENERATION_WAIT_SECONDS);
            response = iamClient.getCredentialReport();
        } while (response.state() != State.COMPLETE);
        return response.content();
    }

    private Collection<AccessKeyData> parseCredentialReport(final SdkBytes credentialReport, final String accountId) throws IOException {
        final List<String[]> records = new ArrayList<>();
        try (final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(credentialReport.asByteArray()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                records.add(line.split(","));
            }
        }

        final List<AccessKeyData> result = new ArrayList<>();
        if (records.size() > 1) {
            final String[] header = records.get(0);
            final List<Integer> headerPositions = new ArrayList<>();
            for (int i = 0; i < header.length; i++) {
                headerPositions.add(i);
            }

            final Integer user = headerPositions.stream().filter(i -> header[i].equals("user")).findFirst().orElse(null);
            final Integer accessKey1Active = headerPositions.stream().filter(i -> header[i].equals("access_key_1_active")).findFirst().orElse(null);
            final Integer accessKey1LastUsedDate = headerPositions.stream().filter(i -> header[i].equals("access_key_1_last_used_date")).findFirst().orElse(null);
            final Integer accessKey2Active = headerPositions.stream().filter(i -> header[i].equals("access_key_2_active")).findFirst().orElse(null);
            final Integer accessKey2LastUsedDate = headerPositions.stream().filter(i -> header[i].equals("access_key_2_last_used_date")).findFirst().orElse(null);

            final List<String[]> dataRows = records.subList(1, records.size());
            if(user != null) {
                dataRows.stream()
                        .map(row -> {
                            AccessKeyData accessKeyData = null;
                            if (accessKey1Active != null && Boolean.parseBoolean(row[accessKey1Active])) {
                                accessKeyData = AccessKeyData.builder()
                                        .accountId(accountId)
                                        .userName(row[user])
                                        .lastUsedDate(accessKey1LastUsedDate != null ? row[accessKey1LastUsedDate] : null)
                                        .build();
                            }
                            return accessKeyData;
                        })
                        .filter(Objects::nonNull)
                        .forEach(result::add);

                dataRows.stream()
                        .map(row -> {
                            AccessKeyData accessKeyData = null;
                            if (accessKey2Active != null && Boolean.parseBoolean(row[accessKey2Active])) {
                                accessKeyData = AccessKeyData.builder()
                                        .accountId(accountId)
                                        .userName(row[user])
                                        .lastUsedDate(accessKey2LastUsedDate != null ? row[accessKey2LastUsedDate] : null)
                                        .build();
                            }
                            return accessKeyData;
                        })
                        .filter(Objects::nonNull)
                        .forEach(result::add);
            }
        }
        return result.stream()
                .filter(accessKeyData -> !accessKeyData.getUserName().equals("<root_account>"))
                .collect(Collectors.toList());
    }


    private IamClient createClient(final String accountIamReaderRole) {
        return IamClient.builder()
                .httpClient(httpClientFactory.create())
                .credentialsProvider(credentialProviderFactory.getCredentials(accountIamReaderRole))
                .region(Region.US_EAST_1)
                .build();
    }
}
