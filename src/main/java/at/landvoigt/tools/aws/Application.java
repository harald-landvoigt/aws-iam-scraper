package at.landvoigt.tools.aws;

import at.landvoigt.tools.aws.iam.AccessKeyData;
import at.landvoigt.tools.aws.iam.IamService;
import at.landvoigt.tools.aws.organization.OrganizationService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

@QuarkusMain
@Slf4j
public class Application implements QuarkusApplication {

    @Inject
    OrganizationService organizationService;
    @Inject
    IamService iamService;

    public int run (String ... args) {
        final Collection<String> accountIds = organizationService.findAllAccounts();
        log.info("Found {} accounts: {}", accountIds.size(), accountIds);
        final Collection<AccessKeyData> iamAccessKeys = iamService.findAccessKeyData(accountIds);

        print(iamAccessKeys);
        return 0;
    }

    private void print(Collection<AccessKeyData> accessKeyDataCollection) {
        final StringWriter sw = new StringWriter();
        final CSVFormat csvFormat = CSVFormat.DEFAULT.builder().build();

        try (final CSVPrinter printer = new CSVPrinter(sw, csvFormat)) {
            printer.println();
            printer.printRecord("AccountId", "UserName", "AccessKeyId", "Status", "CreationDate");
            for (final AccessKeyData accessKeyData : accessKeyDataCollection) {
                printer.printRecord(accessKeyData.getAccountId(),
                        accessKeyData.getUserName(),
                        accessKeyData.getAccessKeyId(),
                        accessKeyData.getStatus(),
                        accessKeyData.getCreationDate());
            }
            log.info(sw.toString());
        } catch (final IOException e) {
            log.error("print csv: {}", e.toString(), e);
        }
    }
}
