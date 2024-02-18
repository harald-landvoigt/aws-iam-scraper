package at.landvoigt.tools.aws;

import jakarta.inject.Singleton;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

@Singleton
public class HttpClientFactory {

    private SdkHttpClient httpClient = UrlConnectionHttpClient.builder().build();

    public SdkHttpClient create() {
        return httpClient;
    }
}
