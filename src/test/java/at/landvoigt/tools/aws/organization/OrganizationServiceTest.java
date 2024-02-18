package at.landvoigt.tools.aws.organization;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class OrganizationServiceTest {

    @Test()
    void invalidAccountIdShouldThrowException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> {new OrganizationService("arn:aws:iam::ORG_ACCOUNT_ID:role/IamScraper-OrganizationAccountsReaderRole", "1234");});
    }
    @Test()
    void nullAccountIdShouldThrowException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> {new OrganizationService("arn:aws:iam::ORG_ACCOUNT_ID:role/IamScraper-OrganizationAccountsReaderRole", null);});
    }

    @Test()
    void validAccountId() {
        OrganizationService organizationService = new OrganizationService("arn:aws:iam::ORG_ACCOUNT_ID:role/IamScraper-OrganizationAccountsReaderRole", "111111111111");
        Assertions.assertEquals(organizationService.getOrganizationReaderRoleArn(), "arn:aws:iam::111111111111:role/IamScraper-OrganizationAccountsReaderRole");
    }

}
