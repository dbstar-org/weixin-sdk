package io.github.dbstarll.weixin.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dbstarll.utils.http.client.HttpClientFactory;
import io.github.dbstarll.weixin.sdk.request.CloudBase;
import io.github.dbstarll.weixin.sdk.request.Color;
import io.github.dbstarll.weixin.sdk.request.GenerateUrlLinkRequest;
import io.github.dbstarll.weixin.sdk.request.UnlimitedQrCodeRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatApiTest {
    private static final String TOKEN_KEY = "WE_CHAT_KEY";

    private String getWeCharKey() {
        final String keyFromProperty = System.getProperty(TOKEN_KEY);
        if (StringUtils.isNotBlank(keyFromProperty)) {
            return keyFromProperty;
        }

        final String opts = System.getenv("MAVEN_OPTS");
        if (StringUtils.isNotBlank(opts)) {
            for (String opt : StringUtils.split(opts)) {
                if (opt.startsWith("-D" + TOKEN_KEY + "=")) {
                    return opt.substring(3 + TOKEN_KEY.length());
                }
            }
        }

        return null;
    }

    private String testAppId;
    private SecretHolder secretHolder;

    @BeforeEach
    void setUp() {
        final String key = getWeCharKey();
        if (StringUtils.isNotBlank(key)) {
            final int index = key.indexOf("=");
            if (index > 0) {
                final String appId = key.substring(0, index);
                final String secret = key.substring(index + 1);
                this.testAppId = appId;
                this.secretHolder = id -> appId.equals(id) ? secret : null;
                return;
            }
        }
        throw new IllegalArgumentException(TOKEN_KEY + " not set");
    }

    @AfterEach
    void tearDown() {
        this.secretHolder = null;
        this.testAppId = null;
    }

    private void useClient(final ThrowingConsumer<HttpClient> consumer) throws Throwable {
        try (CloseableHttpClient client = new HttpClientFactory().build()) {
            consumer.accept(client);
        }
    }

    private void useApi(final ThrowingConsumer<WeChatApi> consumer, final SecretHolder secretHolder) throws Throwable {
        useClient(httpClient -> consumer.accept(new WeChatApi(httpClient, new ObjectMapper(), secretHolder)));
    }

    @Test
    void nullSecretHolder() {
        final Exception e = assertThrowsExactly(NullPointerException.class,
                () -> useApi(api -> {
                }, null));
        assertEquals("secretHolder not set", e.getMessage());
    }

    @Test
    void accessToken() throws Throwable {
        useApi(api -> {
            final AccessTokenResponse response = api.accessToken(testAppId);
            assertNotNull(response.getAccessToken());
            assertEquals(136, response.getAccessToken().length());
            assertTrue(response.getExpiresIn() > 7000);
        }, secretHolder);
    }

    @Test
    void accessTokenInvalidAppId() throws Throwable {
        useApi(api -> {
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.accessToken("appId"));
            assertEquals(40013, e.getStatusCode());
            assertEquals("invalid appid", e.getReasonPhrase());
            assertNotNull(e.getRid());
        }, appId -> "secret");
    }

    @Test
    void accessTokenSecretNotFound() throws Throwable {
        useApi(api -> {
            final NullPointerException e = assertThrowsExactly(NullPointerException.class, () -> api.accessToken("appId"));
            assertEquals("secret not found for appId", e.getMessage());
        }, appId -> null);
    }

    @Test
    void sessionInvalidCode() throws Throwable {
        useApi(api -> {
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.session(testAppId, "code"));
            assertEquals(40029, e.getStatusCode());
            assertEquals("invalid code", e.getReasonPhrase());
            assertNotNull(e.getRid());
        }, secretHolder);
    }

    @Test
    void phoneInvalidAccessToken() throws Throwable {
        useApi(api -> {
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.phone("accessToken", "code"));
            assertEquals(40001, e.getStatusCode());
            assertTrue(e.getReasonPhrase().startsWith("invalid credential, access_token is invalid or not latest"));
            assertNotNull(e.getRid());
        }, secretHolder);
    }

    @Test
    void phoneInvalidCode() throws Throwable {
        useApi(api -> {
            final AccessTokenResponse response = api.accessToken(testAppId);
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.phone(response.getAccessToken(), "code"));
            assertEquals(40029, e.getStatusCode());
            assertTrue(e.getReasonPhrase().startsWith("invalid code"));
            assertNotNull(e.getRid());
        }, secretHolder);
    }

    @Test
    void unlimitedQrCode() throws Throwable {
        useApi(api -> {
            final AccessTokenResponse response = api.accessToken(testAppId);
            final UnlimitedQrCodeRequest request = new UnlimitedQrCodeRequest("a=b");
            request.setEnvVersion("release");
            request.setWidth(430);
            request.setAutoColor(false);
            request.setLineColor(Color.rgb(255, 0, 0));
            request.setIsHyaline(false);
            final byte[] data = api.unlimitedQrCode(response.getAccessToken(), request);
            assertTrue(data.length > 10000);
        }, secretHolder);
    }

    @Test
    void unlimitedQrCodeInvalidPage() throws Throwable {
        useApi(api -> {
            final AccessTokenResponse response = api.accessToken(testAppId);
            final UnlimitedQrCodeRequest request = new UnlimitedQrCodeRequest("a=b", "pages/index/unknown");
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.unlimitedQrCode(response.getAccessToken(), request));
            assertEquals(41030, e.getStatusCode());
            assertEquals("invalid page", e.getReasonPhrase());
            assertNotNull(e.getRid());
        }, secretHolder);
    }

    @Test
    void generateUrlLink() throws Throwable {
        useApi(api -> {
            final GenerateUrlLinkRequest request = new GenerateUrlLinkRequest("pages/home/index", "a=b");
            request.setExpireInterval(1);
            request.setEnvVersion("release");
            final String link = api.generateUrlLink("72_r8liXqV69YuZEVjmpgczW54HpoHC7e1-1P67HadW8fEf5PJQ1gyiGzuVpzIhDLnIgRqAsRsrMcSuWCquSPyxcoYesChvFXqSt07OTNir4iuriJcXgcyj0XvT4dINXObAHAXHY", request);
            assertTrue(link.startsWith("https://wxaurl.cn/"));
        }, secretHolder);
    }

    @Test
    void generateUrlLinkInvalidGrantType() throws Throwable {
        useApi(api -> {
            final AccessTokenResponse response = api.accessToken(testAppId);
            final GenerateUrlLinkRequest request = new GenerateUrlLinkRequest();
            request.setExpireTime(System.currentTimeMillis());
            final CloudBase cloudBase = new CloudBase("release");
            cloudBase.setDomain("qq.com");
            cloudBase.setPath("/abc");
            cloudBase.setQuery("a=b");
            cloudBase.setResourceAppid("appId");
            request.setCloudBase(cloudBase);
            final WeChatResponseException e = assertThrowsExactly(WeChatResponseException.class, () -> api.generateUrlLink(response.getAccessToken(), request));
            assertEquals(40002, e.getStatusCode());
            assertEquals("invalid grant_type", e.getReasonPhrase());
            assertNotNull(e.getRid());
        }, secretHolder);
    }
}