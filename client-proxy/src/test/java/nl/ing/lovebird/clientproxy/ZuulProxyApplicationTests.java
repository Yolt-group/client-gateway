package nl.ing.lovebird.clientproxy;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.controller.ErrorConstants;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.ExpiredJwtTokenSemaEvent;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.JwtTokenInvalidRegexSemaEvent;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.NoAccessTokenSemaEvent;
import nl.ing.lovebird.clientproxy.service.ClientUsersClient;
import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.requester.service.ClientTokenRequesterService;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.errorhandling.ExceptionHandlingService;
import nl.ing.lovebird.logging.MDCContextCreator;
import nl.ing.lovebird.logging.SemaEventLogger;
import org.apache.tomcat.util.codec.binary.Base64;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.LocalHostUriTemplateHandler;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static nl.ing.lovebird.clientproxy.TestTokenCreator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

/*
 *  NOTE WHEN REVIEWING/MODIFYING THIS CLASS
 *  The mockWebServer member field is marked as static, allowing the ContextConfiguration to access the port number that
 *  has been randomly assigned when opening the server socket. The setup() and shutDown() methods are run for every test
 *  and overwrite the mockWebServer member field. This setup feels ugly and prevents tests to run concurrently, but does
 *  allow the mock web server to be restarted after every test, preventing leaks from one test to another test when the
 *  request and/or response queues are not cleared, e.g. due to a failing test.
 */

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ClientProxyApplication.class, ZuulProxyApplicationTests.ContextConfiguration.class}
)
@DirtiesContext
@ActiveProfiles("test")
class ZuulProxyApplicationTests {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_UUID = UUID.fromString(CLIENT_ID);
    static MockWebServer mockWebServer;
    static Optional<ClientToken> clientTokenClient;
    static Optional<ClientUserToken> clientUserTokenClient;

    @Autowired
    private Environment env;
    @Autowired
    private MeterRegistry meterRegistry;
    @SpyBean
    private ClientUsersClient clientUsersClient;

    @SpyBean
    private SemaEventLogger semaEventLogger;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void before() throws IOException, JoseException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        // For tests that want to manipulate interceptors and more on their TestRestTemplate
        restTemplate = new TestRestTemplate();
        restTemplate.setUriTemplateHandler(new LocalHostUriTemplateHandler(env));
        restTemplate.getRestTemplate().getInterceptors()
                .add((request, body, execution) -> {
                    request.getHeaders().add(X_FORWARDED_FOR, "10.0.255.254");
                    return execution.execute(request, body);
                });

        clientTokenClient = Optional.of(TestTokenCreator.createClientToken());
        clientUserTokenClient = Optional.empty();
    }

    @AfterEach
    void after() throws IOException {
        mockWebServer.shutdown();
        clientTokenClient = Optional.empty();
    }

    @Test
    void shouldReturn404ForWrongPath() {
        ResponseEntity<String> result = this.restTemplate.getForEntity("/missing", String.class);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void shouldReturnA200ProxiedRequest() throws InterruptedException {
        final String responseBody = "{\"key\":\"value\"}";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        ResponseEntity<String> result = this.restTemplate.postForEntity("/tokens/tokens", "{}", String.class);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(responseBody, result.getBody());
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/tokens/tokens", request.getPath());
        assertNotNull(request.getHeader("request_trace_id"));
    }

    @Test
    void shouldNotSetPrefixProxyHeader() throws InterruptedException {
        //Explicitly check that proxy headers are not propagated. This screws up the hateoas links.
        final String responseBody = "{\"key\":\"value\"}";

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        ResponseEntity<String> result = this.restTemplate.postForEntity("/tokens/tokens", "{}", String.class);

        assertEquals(responseBody, result.getBody());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/tokens/tokens", request.getPath());
        assertNull(request.getHeader(X_FORWARDED_PREFIX_HEADER.toLowerCase()));
        assertNotNull(request.getHeader(X_FORWARDED_FOR_HEADER.toLowerCase()));
        assertNotNull(request.getHeader(X_FORWARDED_HOST_HEADER.toLowerCase()));
        assertNotNull(request.getHeader(X_FORWARDED_PORT_HEADER.toLowerCase()));
        assertNotNull(request.getHeader(X_FORWARDED_PROTO_HEADER.toLowerCase()));

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void shouldProxyAllUnauthenticatedEndpoints() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));


        ResponseEntity<String> tokenEndpointResult = this.restTemplate.postForEntity("/tokens/tokens", "{}", String.class);
        assertEquals(HttpStatus.OK, tokenEndpointResult.getStatusCode());

        ResponseEntity<String> endpoint = this.restTemplate.postForEntity("/tokens/tokens?andQueryParams=true", null, String.class);
        assertEquals(HttpStatus.OK, endpoint.getStatusCode());
    }

    @Test
    void shouldReturnMatchingErrorMessageWhenAccessTokenMissing() {
        String expectedError = "{\"code\":\"CP" + ErrorConstants.NO_ACCESS_TOKEN.getCode() + "\",\"message\":\"" + ErrorConstants.NO_ACCESS_TOKEN.getMessage() + "\"}";

        String url = "/transactions/transactions-by-account/me";
        ResponseEntity<String> result = this.restTemplate.getForEntity(url, String.class);

        assertEquals(expectedError, result.getBody());
    }

    @Test
    void shouldReturnErrorCodeForExpiredToken() throws Exception {
        String kid = "60f7808d-03dd-42a7-8b62-2f91d0b0cb0a";
        RsaJsonWebKey signatureJwk = (RsaJsonWebKey) RsaJsonWebKey.Factory.newPublicJwk("{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"n\":\"lnI3PMDybQtgSwjSV-PYkkd0GK54aIZ-VRdh-HI-npzRTYE77Es49v0_13-cbGWH7TfFs-HLz7jS-3BGuSTduRKQbvBxk6EWTg6SSJeMc2A_dLSfhGwhZ_NvJSpJ98YcQUtHcZ8K0W-sTyHezyEJ3z1hq8q6P8KATQL9bVy2NMjQ3IEtNRkyV4CncoAeuNGD5Z6xkzhnjnMEqbcoHMb_FMtJ41CFGsEDzPuGYqEz3NvxUZb9_rWZoxKfrcTEOJrwne5nFhdCUcvCe_PID5TrOyyUUXbLRb6jumiVDLN3g8P-WtQaip5UrYUaSUEjjojyMEjQjxSHvo15qkqMxS_yxw\",\"e\":\"AQAB\",\"d\":\"MuSsXvb-i3jfuEJhta20I7fcREUxIlrs_agNUliDanCuNUPUm5jOym7dW-8lYV3vX4YQcUufAMQLS1et9Q_Nmb_38C-SnFhQDVPMlJX_wz_592bq14ckvd-R58aogxMXl9b5cixVIoheh95zWypYBpbjJZRM8SjA8kxios5MLQqFJVLxCbwmD-qHi8XQyGEAwqvqtnlO4JRT8Vya8A4yGd_Da6TWdm487G86U41U6VHOxQXKwvOlOu0uAjuFDckcx_AvcjkHlywqksTWpUUIKVqmcloKcu-qp8rq41XeRlVY8CUQKmJawDrdUXPjrmspW_Lw82ArxbobcyKaO6o0AQ\",\"p\":\"xYIl70LAVCkXRFmiUR74MXpyglgykKdSBHbimcrzwga38LrSxltcm8GH98cUvGG1gl8JKozdZfbdaChvEpC-KO7V1rA-Y-XhhF_tlZNDD9ievb9vqMufUnhjQclnBomjKlh8tIEk-bVYjvuIulyF2SDrrBmLEDs-uJ1M17wHSwE\",\"q\":\"wwAaXbGH6cwF8wpNIlrN1LhkW1v0euWFgEKHL8mCedyET30PNvY1vYguiudNRoOAgAEqi9Iervdl6XImGtuvHbiZgaizFrkZT8n0NqoYqLYxL639SYqQai7PsUbYrJqpL2YnotPX2BB5PtW1ZUQJ-4hf1YTti05Ow3wBLbktpcc\",\"dp\":\"ZQl9SnaFWQhkRKzt4j3LjdQr_A4OX_2YcXw306EFLb6uHlIUPTDDoVJRsil_rBb3-aeQUtoY8G5nOT9mAsNU5C-56MfkQsp4oXVJXvkkl1ijbEIgZuMzr8ayUBctwyRp-eGmediPB8cDdLGsclmeh0LWDQZMI5OLNHoTs1EXEgE\",\"dq\":\"LFGmrGq_-CwtofpSY599rn4mGPmCTDhEKk10ijDjXaz3yVUkExrMRgJgiaNeVctndjBNqi-cV6nU2MTf0jThzQB6qxRbd6ukDBVbUt0_84BNF4gUzBUZE3kGLUVr03bnQuWV1pUNNocv9079BkH7ftaU6WNn1cR7dESHxAuVS1s\",\"qi\":\"qSgt0X7ZFYaN04bRG2UeCmXdBamXA7HhUBhFPz0CqDVhWqcnYJAbVRRODBkA5JJookAaHAvgp_qrtOkyCMDIErVxzSN2ohJiGNTXnXrFFQg_aFauRfG2IkcM0ZETmas7nGGpuWz7dKVc5l6_NGODpzFsw_3GInjUqMw51JStzyY\"}");
        String expectedError = "{\"code\":\"CP" + ErrorConstants.EXPIRED_TOKEN.getCode() + "\",\"message\":\"" + ErrorConstants.EXPIRED_TOKEN.getMessage() + "\"}";
        JwtClaims claims = createJwtClaims();
        NumericDate expirationDate = NumericDate.now();
        expirationDate.addSeconds(-5);
        claims.setExpirationTime(expirationDate);
        signatureJwk.getRsaPrivateKey();
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        this.restTemplate.getRestTemplate().getInterceptors().add(new TokenAuthorizationInterceptor(accessToken));

        ResponseEntity<String> result = this.restTemplate.getForEntity("/transactions/transactions-by-account/me", String.class);

        List<String> wwwAuthenticateHeaders = result.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE);
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals(expectedError, result.getBody());
        assertTrue(wwwAuthenticateHeaders.get(0).contains("invalid_token"));

        verify(semaEventLogger).logEvent(new ExpiredJwtTokenSemaEvent("/transactions/transactions-by-account/me", "10.0.255.254"));
    }

    @Test
    void shouldReturn504OnConnectionTimeout() {
        String url = "/tokens/tokens";
        String expectedCode = "CP" + ErrorConstants.TIMEOUT.getCode();
        String expectedMessage = ErrorConstants.TIMEOUT.getMessage();

        mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        ResponseEntity<ErrorDTO> response = this.restTemplate.postForEntity(url, null, ErrorDTO.class);

        ErrorDTO body = response.getBody();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), response.getStatusCodeValue());
        assertEquals(expectedCode, body.getCode());
        assertEquals(expectedMessage, body.getMessage());
    }

    @Test
    void shouldReturn404OnNonMappedUnpstreamService() {
        String url = "/something-unknown";
        String expectedCode = "CP" + ErrorConstants.NOT_FOUND.getCode();
        String expectedMessage = ErrorConstants.NOT_FOUND.getMessage();


        ResponseEntity<ErrorDTO> response = this.restTemplate.getForEntity(url, ErrorDTO.class);

        ErrorDTO body = response.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCodeValue());
        assertEquals(expectedCode, body.getCode());
        assertEquals(expectedMessage, body.getMessage());
    }

    @Test
    void shouldReturn502OnUnresolvableUpstreamService() {
        String url = "/non-resolveable-host/";
        String expectedCode = "CP" + ErrorConstants.UNKNOWN_HOST.getCode();
        String expectedMessage = ErrorConstants.UNKNOWN_HOST.getMessage();

        ResponseEntity<ErrorDTO> response = this.restTemplate.getForEntity(url, ErrorDTO.class);

        ErrorDTO body = response.getBody();
        assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCodeValue());
        assertEquals(expectedCode, body.getCode());
        assertEquals(expectedMessage, body.getMessage());
    }

    @Test
    void shouldRejectAuthenticatedEndpointWithoutToken() {
        ResponseEntity<String> transactionsEndpointResult = this.restTemplate.getForEntity("/transactions/transactions-by-account/me", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, transactionsEndpointResult.getStatusCode());
        verify(semaEventLogger).logEvent(new NoAccessTokenSemaEvent("/transactions/transactions-by-account/me", "10.0.255.254"));
    }

    @Test
    void shouldProxyAuthenticatedEndpointWithToken() throws Exception {
        String token = TestTokenCreator.createValidAccessToken();
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));

        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, clientUserId.toString());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> transactionsEndpointResult = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, transactionsEndpointResult.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest.getHeader("client-id")); // Set by AccessTokenValidationFilter.run
    }

    @Test
    void shouldNotPropagateUserId() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestTokenCreator.createValidAccessToken();
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(CLIENT_UUID, clientUserId, UUID.randomUUID());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.USER_ID_HEADER_NAME, userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> transactionsEndpointResult = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, transactionsEndpointResult.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertNull(recordedRequest.getHeader("user-id"), "should not propagate a userId that's provided externally");
    }

    @Test
    void shouldAddClientIdToHttpServerRequestMetricsInMicroMeter() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestTokenCreator.createValidAccessToken();
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(CLIENT_UUID, clientUserId, UUID.randomUUID());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.USER_ID_HEADER_NAME, userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> transactionsEndpointResult = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, transactionsEndpointResult.getStatusCode());

        assertThat(meterRegistry.get("http.server.requests").meter().getId().getTags()).contains(Tag.of("client-id", CLIENT_ID));
    }

    @Test
    void shouldReturn500WithAcceptJsonOnFilterException() throws Exception {
        String token = TestTokenCreator.createValidAccessToken();

        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, UUID.randomUUID().toString());
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> transactionsEndpointResult = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, transactionsEndpointResult.getStatusCode());
        assertThat(transactionsEndpointResult.getBody()).isEqualTo("{\"code\":\"CP999\",\"message\":\"Unknown error\"}");
    }

    @Test
    void shouldReturn400BadRequestOnInvalidClientUserId() throws Exception {
        String token = TestTokenCreator.createValidAccessToken();
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(CLIENT_UUID, clientUserId, UUID.randomUUID());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, "invalid-uuid");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> result = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void shouldReturn423LockedOnBlockedClientUser() throws Exception {
        String token = TestTokenCreator.createValidAccessToken();
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, true, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, clientUserId.toString());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> result = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.LOCKED, result.getStatusCode());
    }

    @Test
    void shouldReturn400BadRequestOnInvalidRequestTraceId() {
        String expectedError = "{\"code\":\"CP" + ErrorConstants.ILLEGAL_ARGUMENT.getCode() + "\",\"message\":\"" + ErrorConstants.ILLEGAL_ARGUMENT.getMessage() + "\"}";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, "notTheUUID");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> result = restTemplate.postForEntity("/tokens/tokens", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals(expectedError, result.getBody());
    }

    @Test
    void shouldReturn400BadRequestOnInvalidJWT() {
        String expectedError = "{\"code\":\"CP" + ErrorConstants.INVALID_JWT.getCode() + "\",\"message\":\"" + ErrorConstants.INVALID_JWT.getMessage() + "\"}";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer no.valid.jwt");
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, UUID.randomUUID().toString());
        headers.set(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, UUID.randomUUID().toString());
        HttpEntity<?> entity = new HttpEntity<>(headers);


        ResponseEntity<String> result = restTemplate.exchange("/transactions/transactions-by-account/me", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals(expectedError, result.getBody());
        assertEquals(0, mockWebServer.getRequestCount());
        verify(semaEventLogger).logEvent(new JwtTokenInvalidRegexSemaEvent("/transactions/transactions-by-account/me", "10.0.255.254"));
    }

    @Test
    void shouldProxyAuthenticatedEndpointWithTokenReturning400Response() throws Exception {
        final String token = TestTokenCreator.createValidAccessToken();
        mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"key\":\"value\"}"));
        restTemplate.getRestTemplate().getInterceptors().add(new TokenAuthorizationInterceptor(token));

        ResponseEntity<String> transactionsEndpointResult = restTemplate.getForEntity("/transactions/transactions-by-account/me", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, transactionsEndpointResult.getStatusCode());
    }

    @Test
    void shouldProxyAuthenticatedEndpointWithTokenReturning500Response() throws Exception {
        final String token = TestTokenCreator.createValidAccessToken();
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"key\":\"value\"}"));
        restTemplate.getRestTemplate().getInterceptors().add(new TokenAuthorizationInterceptor(token));

        ResponseEntity<String> transactionsEndpointResult = restTemplate.getForEntity("/transactions/transactions-by-account/me", String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, transactionsEndpointResult.getStatusCode());
    }

    @Test
    void shouldPropagateInternalHeadersForAuthenticatedRoute() throws Exception {
        final UUID clientUserId = UUID.randomUUID();
        final UUID fakeClientId = UUID.randomUUID();
        final UUID fakeClientToken = UUID.randomUUID();
        final UUID fakeUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));

        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        String encryptedAccessToken = TestTokenCreator.createValidAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + encryptedAccessToken);
        headers.set(MDCContextCreator.CLIENT_ID_HEADER_NAME, fakeClientId.toString());
        headers.set(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME, fakeClientToken.toString());
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, clientUserId.toString());
        headers.set(MDCContextCreator.USER_ID_HEADER_NAME, fakeUserId.toString());

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String url = "/transactions/transactions-by-account/me";
        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        // Assert for the real id's (not the ones injected)
        assertEquals(CLIENT_ID, recordedRequest.getHeader("client-id"));
        assertEquals(clientUserTokenClient.get().getSerialized(), recordedRequest.getHeader("client-token"));
        assertEquals(USER_ID.toString(), recordedRequest.getHeader("user-id"));
        assertEquals(clientUserId.toString(), recordedRequest.getHeader("client-user-id"));
    }


    @Test
    void shouldOverwriteUserGivenClientIdBaggageHeader() throws JoseException, InterruptedException {
        final UUID clientUserId = UUID.randomUUID();
        final UUID fakeClientId = UUID.randomUUID();
        final UUID fakeClientToken = UUID.randomUUID();
        final UUID fakeUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));

        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        String encryptedAccessToken = TestTokenCreator.createValidAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + encryptedAccessToken);
        headers.set(MDCContextCreator.CLIENT_ID_HEADER_NAME, fakeClientId.toString());
        headers.set(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME, fakeClientToken.toString());
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, clientUserId.toString());
        headers.set(MDCContextCreator.USER_ID_HEADER_NAME, fakeUserId.toString());

        // add baggage
        headers.set("baggage-client-id", new UUID(0, 0).toString());

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String url = "/transactions/transactions-by-account/me";
        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(CLIENT_ID, recordedRequest.getHeader("baggage-client-id")); // http header
        assertEquals(CLIENT_ID, recordedRequest.getHeader("baggage_client-id")); // kafka header
    }

    @Test
    void shouldNotPropagateInternalHeadersForOpenRoute() throws Exception {
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        final UUID fakeClientId = UUID.randomUUID();
        final UUID fakeClientToken = UUID.randomUUID();
        final UUID fakeClientUserId = UUID.randomUUID();
        final UUID fakeUserId = UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(MDCContextCreator.CLIENT_ID_HEADER_NAME, fakeClientId.toString());
        headers.set(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME, fakeClientToken.toString());
        headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, fakeClientUserId.toString());
        headers.set(MDCContextCreator.USER_ID_HEADER_NAME, fakeUserId.toString());

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> result = restTemplate.exchange("/tokens/tokens", HttpMethod.POST, entity, String.class);
        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        // Assert for absence of headers
        assertNull(recordedRequest.getHeader("client-id"));
        assertNull(recordedRequest.getHeader("client-token"));
        assertNull(recordedRequest.getHeader("user-id"));
        assertNull(recordedRequest.getHeader("client-user-id"));
    }

    @Test
    void when_theRouteContainsUserIdInThePath_then_itShouldUseThatRatherThenClientUserIdHeader() throws JoseException, InterruptedException {
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        String token = TestTokenCreator.createValidAccessToken();
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null)))
                .when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> accountsEndpointResult = restTemplate.exchange("/users/{userId}/accounts", HttpMethod.GET, entity, String.class, clientUserId);

        assertEquals(HttpStatus.OK, accountsEndpointResult.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(USER_ID.toString(), recordedRequest.getHeader("user-id"));
        assertThat(recordedRequest.getPath()).isEqualTo("/accounts-and-transactions/users/" + USER_ID + "/accounts");

    }

    @Test
    void when_theRequestStartsWithAVersionedUserRequest() throws JoseException, InterruptedException {
        String token = TestTokenCreator.createValidAccessToken();
        clientTokenClient = Optional.of(createClientToken());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange("/v1/users", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/client-users/v1/users");
    }

    @Test
    void when_theRequestHasKYCEntitiesEnabled() throws JoseException, InterruptedException {
        String token = TestTokenCreator.createValidAccessToken();
        clientTokenClient = Optional.of(createClientToken(claims -> {
            claims.setClaim(ClientTokenConstants.CLAIM_PSD2_LICENSED, false);
            claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_PRIVATE_INDIVIDUALS, false);
            claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_ENTITIES, true);
            claims.setClaim(ClientTokenConstants.CLAIM_ONE_OFF_AIS, false);
        }));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);


        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange("/v2/users", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/client-users/v2/users");
    }

    @Test
    void when_theRequestHasKYCDisabled() throws JoseException, InterruptedException {
        String token = TestTokenCreator.createValidAccessToken();
        clientTokenClient = Optional.of(createClientToken(claims -> {
            claims.setClaim(ClientTokenConstants.CLAIM_PSD2_LICENSED, true);
            claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_PRIVATE_INDIVIDUALS, false);
            claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_ENTITIES, false);
        }));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange("/v2/users", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/client-users/v2/users");
    }

    @Test
    void when_theRequestIsACreateUserRequestForAKYCPrivateIndividualsUser() throws JoseException, InterruptedException {
        String token = TestTokenCreator.createValidAccessToken();
        ClientToken clientToken = createClientToken(claims -> {
            claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_PRIVATE_INDIVIDUALS, true);
            claims.setClaim(ClientTokenConstants.CLAIM_ONE_OFF_AIS, false);
        });
        clientTokenClient = Optional.of(clientToken);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange("/v1/users", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/client-users/v1/users");
    }

    @Test
    void when_theRequestStartsWithAVersionedUserRequestIncludingClientUserMapping() throws JoseException, InterruptedException {
        UUID clientUserId = UUID.randomUUID();
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserId.toString()));
        String token = TestTokenCreator.createValidAccessToken();
        doReturn(Optional.of(new UserDTO(UUID.fromString(CLIENT_ID), clientUserId, USER_ID, false, null, null, null, false, LocalDateTime.now(), null))).when(clientUsersClient)
                .getClientUser(eq(CLIENT_UUID), eq(clientUserId), any());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> response = restTemplate.exchange("/v1/users/{userId}", HttpMethod.DELETE, entity, String.class, clientUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(USER_ID.toString(), recordedRequest.getHeader("user-id"));
        assertThat(recordedRequest.getPath()).isEqualTo("/client-users/v1/users/" + USER_ID);
    }

    @Test
    void when_AClientUsesAClientUserThatBelongsToAnotherClient_then_itShouldReturnA404() throws JoseException, InterruptedException {
        UserDTO clientUserClientA = new UserDTO(UUID.fromString(CLIENT_ID), UUID.randomUUID(), USER_ID, false, null, null, null, false, LocalDateTime.now(), null);
        UserDTO clientUserClientB = new UserDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, null, null, null, false, LocalDateTime.now(), null);
        // Default: no client.
        doReturn(Optional.empty()).when(clientUsersClient).getClientUser(any(), any(), any());
        // We have clientuser1 of client A, and clientuser2 of client B:
        doReturn(Optional.of(clientUserClientA)).when(clientUsersClient)
                .getClientUser(eq(clientUserClientA.getClientId()), eq(clientUserClientA.getClientUserId()), any());
        doReturn(Optional.of(clientUserClientB)).when(clientUsersClient)
                .getClientUser(eq(clientUserClientB.getClientId()), eq(clientUserClientB.getClientUserId()), any());

        String accessTokenOfClientA = TestTokenCreator.createValidAccessToken();

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"key\":\"value\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOfClientA);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        clientUserTokenClient = Optional.of(getClientUserToken(clientUserClientA.getClientUserId().toString()));
        ResponseEntity<String> okResponse = restTemplate.exchange("/users/{userId}/transactions", HttpMethod.GET, entity, String.class, clientUserClientA.getClientUserId());
        // Client A (accessTokenOfClientA is used) tries to access clientUserB. That should fail.
        clientUserTokenClient = Optional.of(getClientUserToken(clientUserClientB.getClientUserId().toString()));
        ResponseEntity<String> notFound = restTemplate.exchange("/users/{userId}/transactions", HttpMethod.GET, entity, String.class, clientUserClientB.getClientUserId());

        assertEquals(HttpStatus.OK, okResponse.getStatusCode());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/accounts-and-transactions/users/" + clientUserClientA.getUserId() + "/transactions");

        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertThat(notFound.getBody()).contains("Unknown client-user");
    }

    @Test
    void shouldDenyARequestThroughZuulServlet() {
        ResponseEntity<String> result = this.restTemplate.postForEntity("/zuul/tokens/tokens", "{}", String.class);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    static class TokenAuthorizationInterceptor implements ClientHttpRequestInterceptor {

        private final String token;

        public TokenAuthorizationInterceptor(String token) {
            this.token = token;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
            return execution.execute(request, body);
        }
    }

    private ClientUserToken getClientUserToken(String clientUserId) {
        UUID clientGroupId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims();
        claims.setClaim("sub", "client:" + CLIENT_ID);
        claims.setClaim("client-id", CLIENT_ID);
        claims.setClaim("client-group-id", clientGroupId);
        claims.setClaim("isf", "site-management");
        claims.setClaim("psd2-licensed", false);
        claims.setClaim("ais", true);
        claims.setClaim("pis", true);
        claims.setClaim("cam", true);
        claims.setClaim("client-users-kyc-private-individuals", true);
        claims.setClaim("client-users-kyc-entities", false);
        claims.setClaim("data_enrichment_categorization", true);
        claims.setClaim("data_enrichment_merchant_recognition", true);
        claims.setClaim("data_enrichment_cycle_detection", false);
        claims.setClaim("data_enrichment_labels", false);
        claims.setClaim("one_off_ais", false);
        claims.setClaim("user-id", USER_ID.toString());
        claims.setClaim("client-user-id", clientUserId);
        String serialized = Base64.encodeBase64String(String.format("fake-client-token-for-%s", CLIENT_ID).getBytes());
        return new ClientUserToken(serialized, claims);
    }

    @Configuration
    @Import({ExceptionHandlingService.class})
    static class ContextConfiguration {

        @Bean
        @Primary
        public ClientTokenRequesterService clientTokenRequesterService(
                @Value("${yolt.client-token.requester.signing-keys.client-gateway}") String signingJwk) {
            return new ClientTokenRequesterService(null, signingJwk, null, null) {
                @Override
                public ClientToken getClientToken(UUID clientId) {
                    return clientTokenClient.orElse(null);
                }

                @Override
                public ClientUserToken getClientUserToken(UUID clientId, UUID userId) {
                    return clientUserTokenClient.orElse(null);
                }
            };
        }

        @Bean
        public RouteAllTrafficToMockServerFilter testFilter() {
            return new RouteAllTrafficToMockServerFilter();
        }

        class RouteAllTrafficToMockServerFilter extends ZuulFilter {

            @Autowired
            private Environment env;

            @Override
            public String filterType() {
                return FilterType.PRE.getType();
            }

            @Override
            public int filterOrder() {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean shouldFilter() {
                return true;
            }

            @Override
            public Object run() {
                RequestContext currentContext = RequestContext.getCurrentContext();
                URL routeHost = currentContext.getRouteHost();
                final String port;
                try {
                    if (currentContext.get("proxy").equals("user-context")) {
                        port = env.getProperty("local.server.port");
                    } else {
                        port = "" + mockWebServer.getPort();
                    }
                    final URL url = new URL(routeHost.toString().replaceAll(":[0-9]{4}", ":" + port));

                    currentContext.setRouteHost(url);
                } catch (MalformedURLException e) {
                    fail("something went wrong. failure during test-filter.");
                }
                return null;
            }
        }
    }
}
