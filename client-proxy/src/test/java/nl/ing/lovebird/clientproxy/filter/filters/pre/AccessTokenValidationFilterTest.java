package nl.ing.lovebird.clientproxy.filter.filters.pre;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.TestTokenCreator;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.InvalidJwtTokenSemaEvent;
import nl.ing.lovebird.logging.SemaEventLogger;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.ReservedClaimNames;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import static nl.ing.lovebird.clientproxy.TestTokenCreator.*;
import static nl.ing.lovebird.clientproxy.filter.filters.pre.AccessTokenValidationFilter.MAX_ACCESS_TOKEN_LENGTH;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidationFilterTest {

    private static final String SIGNATURE_KID = UUID.randomUUID().toString();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    @Mock
    SemaEventLogger semaEventLogger;
    private AccessTokenValidationFilter accessTokenValidationFilter;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @BeforeEach
    void setup() throws Exception {
        RsaJsonWebKey jsonWebKey = RsaJwkGenerator.generateJwk(2048, "BC", new SecureRandom());
        jsonWebKey.setKeyId(SIGNATURE_KID);

        accessTokenValidationFilter = new AccessTokenValidationFilter(ENCRYPTION_SECRET, new ZuulHeadersHelper(), semaEventLogger);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);

        Logger logger = (Logger) LoggerFactory.getLogger(AccessTokenValidationFilter.class.getName());
        logger.addAppender(mockAppender);
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void shouldHaveFilterTypePre() {
        assertEquals(FilterType.PRE.getType(), accessTokenValidationFilter.filterType());
    }

    @Test
    void shouldHaveFilterOrder() {
        assertEquals(ApplicationConfiguration.FILTER_ORDER_ACCESSTOKEN_FILTER, accessTokenValidationFilter.filterOrder());
        assertForwardedRequest();
    }

    @Test
    void shouldSetClientIdInContextForRequestsWithValidTokenWithInnerClientToken() throws JoseException {
        JwtClaims claims = createJwtClaims();
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertEquals(CLIENT_ID, RequestContext.getCurrentContext().get(ApplicationConfiguration.CLIENT_ID_HEADER_NAME));
        assertForwardedRequest();
    }

    @Test
    void shouldSetClientIdInContextForRequestsWithValidToken() throws JoseException {
        JwtClaims claims = createJwtClaims();
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertEquals(CLIENT_ID, RequestContext.getCurrentContext().get(ApplicationConfiguration.CLIENT_ID_HEADER_NAME));
        assertForwardedRequest();
    }

    @Test
    void shouldSetClientIdInHeaderForRequestsWithValidToken() throws JoseException {
        JwtClaims claims = createJwtClaims();
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertEquals(CLIENT_ID,
                RequestContext.getCurrentContext().getZuulRequestHeaders().get(ApplicationConfiguration.CLIENT_ID_HEADER_NAME));
        assertForwardedRequest();
    }

    @Test
    void validateImplicitSigningInEncryptedAccessToken() throws JoseException {
        JwtClaims claims1 = createJwtClaims();
        String jwe1 = TestTokenCreator.createAccessToken(createJsonWebKey(), claims1.toJson());

        JwtClaims claims2 = createJwtClaims();
        claims2.setClaim("test", "test");
        String jwe2 = TestTokenCreator.createAccessToken(createJsonWebKey(), claims2.toJson());

        // override the encrypted payload from jwe1 into jwe2
        String[] splitJwe2 = jwe2.split("\\.");
        splitJwe2[3] = jwe1.split("\\.")[3];
        String accessToken = StringUtils.join(splitJwe2, ".");

        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");

        verify(mockAppender).doAppend(ArgumentMatchers.argThat(argument -> {
            assertThat(argument.getFormattedMessage(), containsString("Denying request because something is wrong with the token to  from IP 127.0.0.1"));
            assertThat(argument.getThrowableProxy().getCause().getClassName(), equalTo("org.jose4j.lang.IntegrityException"));
            assertThat(argument.getThrowableProxy().getCause().getMessage(), containsString("Authentication tag check failed."));
            return true;
        }));

        verify(semaEventLogger).logEvent(new InvalidJwtTokenSemaEvent("", "127.0.0.1"));
    }

    @Test
    void shouldFailBigAccessToken() throws JoseException {
        JwtClaims claims = createJwtClaims();
        // loop to include bogus claims in order to make a token which is bigger than 2000 chars.
        for (int i = 0; i < 100; i++) {
            claims.setClaim("newClaim" + i, "elevenchars");
        }
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        assertTrue(accessToken.length() > MAX_ACCESS_TOKEN_LENGTH, "Failed generating large token");
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");
    }

    @Test
    void shouldNotSetClientIdInRequestsWithoutAuthorizationHeader() {
        accessTokenValidationFilter.run();

        assertFalse(RequestContext.getCurrentContext().containsKey(ApplicationConfiguration.CLIENT_ID_HEADER_NAME));

        assertRejectedRequest(HttpStatus.UNAUTHORIZED, "{\"code\":\"CP002\",\"message\":\"No Access Token on request\"}");
    }

    @Test
    void shouldNotSetClientIdRequestsWithAuthorizationTypeDifferentThenBearer() {
        request.addHeader("Authorization", "Basic U29tZVVzZXI6U3R1cGlkUGFzc3dvcmQ=");

        accessTokenValidationFilter.run();

        assertFalse(RequestContext.getCurrentContext().containsKey(ApplicationConfiguration.CLIENT_ID_HEADER_NAME));
        assertRejectedRequest(HttpStatus.UNAUTHORIZED, "{\"code\":\"CP002\",\"message\":\"No Access Token on request\"}");
    }

    @Test
    void shouldDisallowRequestsWithInvalidBearerToken() {
        request.addHeader("Authorization", "Bearer invalid-token");

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");
    }

    @Test
    void shouldDisallowRequestsWithoutSubject() throws JoseException {
        JwtClaims claims = createJwtClaims();
        claims.unsetClaim(ReservedClaimNames.SUBJECT);
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));
        request.setRequestURI("/transactions/transactions-by-account/me");

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");

        verify(semaEventLogger).logEvent(new InvalidJwtTokenSemaEvent("/transactions/transactions-by-account/me", "127.0.0.1"));
    }

    @Test
    void shouldDisallowRequestsWithoutExpirationTime() throws JoseException {
        JwtClaims claims = createJwtClaims();
        claims.unsetClaim(ReservedClaimNames.EXPIRATION_TIME);
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");
    }

    @Test
    void shouldDisallowRequestsWithExpiredToken() throws JoseException {
        JwtClaims claims = createJwtClaims();
        NumericDate expirationDate = NumericDate.now();
        expirationDate.addSeconds(-5);
        claims.setExpirationTime(expirationDate);
        String accessToken = TestTokenCreator.createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.UNAUTHORIZED, "{\"code\":\"CP012\",\"message\":\"Access token is expired\"}");
    }

    @Test
    void shouldDisallowRequestsWithTokensCreatedWithAnotherKey() throws JoseException {
        JwtClaims claims = createJwtClaims();
        String accessToken = TestTokenCreator.createAccessToken(createWrongJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));

        accessTokenValidationFilter.run();

        assertRejectedRequest(HttpStatus.BAD_REQUEST, "{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");
    }

    @Test
    void testJwePattern() {
        Pattern pattern = AccessTokenValidationFilter.JWE_PATTERN;
        assertTrue(pattern.matcher("abcdefghij.abcdefghij.abcdefghij.abcdefghij.abcdefghij").matches());
        assertTrue(pattern.matcher("abcdefghij...abcdefghij.abcdefghij-_").matches(), "Special chars");
        assertTrue(pattern.matcher("abcdefghij....abcdefghij").matches(), "Empty part");

        assertFalse(pattern.matcher("abcdefghij...abcdefghij.abcdefghij$").matches(), "Invalid char");
        assertFalse(pattern.matcher("abcdefghij.abcdefghij").matches(), "Two parts");
        assertFalse(pattern.matcher("abcdefghij.abcdefghij.abcdefghij").matches(), "Three parts");
        assertFalse(pattern.matcher("abcdefghij.abcdefghij.abcdefghij.abcdefghij").matches(), "Four parts");
    }

    @Test
    void shouldFailSanityCheck() {
        accessTokenValidationFilter.checkAccessTokenFormat("a", "", "");
    }

    @Test
    void shouldFailAccessTokensMaxLengthCheck() {
        char[] charArray = new char[MAX_ACCESS_TOKEN_LENGTH];
        Arrays.fill(charArray, 'a');
        String tooLongAccessToken = "abcdefghij...." + new String(charArray);

        accessTokenValidationFilter.checkAccessTokenFormat(tooLongAccessToken, "", "");
    }

    private void assertForwardedRequest() {
        RequestContext ctx = RequestContext.getCurrentContext();
        assertThat(ctx.sendZuulResponse(), is(true));
    }

    private void assertRejectedRequest(HttpStatus httpStatus, String body) {
        RequestContext ctx = RequestContext.getCurrentContext();

        assertThat(ctx.sendZuulResponse(), is(false));
        assertThat(ctx.getResponseStatusCode(), is(httpStatus.value()));
        assertThat(ctx.getResponseBody(), is(body));
    }
}
