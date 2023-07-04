package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.MalformedJwtClaimSemaEvent;
import nl.ing.lovebird.logging.SemaEventLogger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.security.Security;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.TestTokenCreator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidationFilterMockedTest {

    private static final String SIGNATURE_KID = UUID.randomUUID().toString();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private AccessTokenValidationFilter accessTokenValidationFilter;

    @Mock
    private SemaEventLogger semaEventLogger;

    @BeforeEach
    void setup() throws Exception {
        RsaJsonWebKey jsonWebKey = RsaJwkGenerator.generateJwk(2048, "BC", new SecureRandom());
        jsonWebKey.setKeyId(SIGNATURE_KID);

        accessTokenValidationFilter = new AccessTokenValidationFilter(ENCRYPTION_SECRET, new ZuulHeadersHelper(), semaEventLogger);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    /**
     * Since this test is testing a situation that cannot really happen (unless the jose4j library is changed),
     * we'll need to mock the JwtConsumer consumer here to simulate the behaviour. This is the reason for this
     * test to be in a separate class.
     */
    @Test
    void testMalformedClaimException() throws Exception {
        JwtClaims claims = createJwtClaims();
        claims.setClaim("sub", 1234L);

        JwtConsumer jwtConsumer = mock(JwtConsumer.class);
        JwtContext jwtContext = mock(JwtContext.class);
        when(jwtConsumer.process(anyString())).thenReturn(jwtContext);
        when(jwtContext.getJwtClaims()).thenReturn(claims);
        ReflectionTestUtils.setField(accessTokenValidationFilter, "jwtConsumer", jwtConsumer);
        String accessToken = createAccessToken(createJsonWebKey(), claims);
        request.addHeader("Authorization", String.format("Bearer %s", accessToken));
        request.setRequestURI("/some/uri");

        accessTokenValidationFilter.run();

        assertRejectedRequest();

        verify(semaEventLogger).logEvent(new MalformedJwtClaimSemaEvent("/some/uri", "127.0.0.1"));
    }

    private void assertRejectedRequest() {
        RequestContext ctx = RequestContext.getCurrentContext();

        assertThat(ctx.sendZuulResponse()).isFalse();
        assertThat(ctx.getResponseStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(ctx.getResponseBody()).isEqualTo("{\"code\":\"CP001\",\"message\":\"Invalid JWT\"}");
    }

}
