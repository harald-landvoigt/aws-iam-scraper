package at.landvoigt.tools.aws.iam;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class AccessKeyData {
    private String accountId;
    private String accessKeyId;
    private String userName;
    private String status;
    private String creationDate;
    private String lastUsedDate;
}
