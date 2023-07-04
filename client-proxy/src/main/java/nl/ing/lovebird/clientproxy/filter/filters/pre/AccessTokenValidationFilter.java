package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.controller.ErrorConstants;
import nl.ing.lovebird.clientproxy.controller.ZuulRequestRejecter;
import nl.ing.lovebird.clientproxy.exception.InvalidJwtConsumerConfigurationException;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.filter.filters.pre.sema.*;
import nl.ing.lovebird.logging.SemaEventLogger;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKey.Factory;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.JoseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.CLIENT_ID_HEADER_NAME;
import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_ACCESSTOKEN_FILTER;

@Slf4j
public class AccessTokenValidationFilter extends ZuulFilter {
    // Basic format of 3 or 5 parts with limited set of allowed characters
    static final Pattern JWE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]*\\.[a-zA-Z0-9\\-_]*\\.[a-zA-Z0-9\\-_]*\\.[a-zA-Z0-9\\-_]*\\.[a-zA-Z0-9\\-_]*$");
    static final int MAX_ACCESS_TOKEN_LENGTH = 2000;

    private final JwtConsumer jwtConsumer;
    private final ZuulHeadersHelper zuulHeadersHelper;
    private final SemaEventLogger semaEventLogger;

    public static final String TOKEN_HEADER_ENCRYPTION_KEY_TYPE = "kty";
    public static final String TOKEN_HEADER_ENCRYPTION_KEY = "k";
    public static final String TOKEN_HEADER_ENCRYPTION_KEY_TYPE_OCTET_SEQUENCE = "oct";
    public static final String TOKEN_EXTRA_CLAIM_SUBJECT_IP = "sub-ip";

    public AccessTokenValidationFilter(String encryptionSecret,
                                       ZuulHeadersHelper zuulHeadersHelper,
                                       SemaEventLogger semaEventLogger) {
        this.zuulHeadersHelper = zuulHeadersHelper;
        this.semaEventLogger = semaEventLogger;
        try {

            AlgorithmConstraints algConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, KeyManagementAlgorithmIdentifiers.DIRECT);
            AlgorithmConstraints encConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, ContentEncryptionAlgorithmIdentifiers.AES_256_CBC_HMAC_SHA_512);

            Map<String, Object> headers = new HashMap<>();
            headers.put(TOKEN_HEADER_ENCRYPTION_KEY_TYPE, TOKEN_HEADER_ENCRYPTION_KEY_TYPE_OCTET_SEQUENCE);
            headers.put(TOKEN_HEADER_ENCRYPTION_KEY, encryptionSecret);
            JsonWebKey decryptionJwk = Factory.newJwk(headers);

            jwtConsumer = new JwtConsumerBuilder()
                    .setRequireSubject()
                    .setRequireExpirationTime()
                    .setEnableRequireEncryption()
                    .setDecryptionKey(decryptionJwk.getKey())
                    .setJweContentEncryptionAlgorithmConstraints(encConstraints)
                    .setJweAlgorithmConstraints(algConstraints)
                    .setDisableRequireSignature()
                    .setEnableRequireIntegrity()
                    .build();
        } catch (JoseException e) {
            throw new InvalidJwtConsumerConfigurationException(e);
        }
    }

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_ACCESSTOKEN_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        return ctx.sendZuulResponse() && (Boolean) ctx.get(ApplicationConfiguration.AUTHENTICATION_REQUIRED);
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        String authorizationHeader = request.getHeader("Authorization");
        String originIpName = request.getRemoteAddr();
        String requestURI = request.getRequestURI();
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.error("Received unauthenticated request (no access token) to {} from IP {}",
                    requestURI, originIpName);
            semaEventLogger.logEvent(new NoAccessTokenSemaEvent(requestURI, originIpName));

            ZuulRequestRejecter.reject(ctx, ErrorConstants.NO_ACCESS_TOKEN, HttpStatus.UNAUTHORIZED);
            return null;
        }

        try {
            String accessToken = authorizationHeader.substring("Bearer ".length());

            // Sanity check before handing off to Jose4J, as requested by security audit
            if (!checkAccessTokenFormat(accessToken, originIpName, requestURI)) {
                ZuulRequestRejecter.reject(ctx, ErrorConstants.INVALID_JWT, HttpStatus.BAD_REQUEST);
                return null;
            }

            JwtContext jwtContext = jwtConsumer.process(accessToken);

            // log is disabled. In case it's necessary for production debugging you can enable it with deployment-config (overriding a
            // property in application.yml)
            log.info("request.getRemoteAddr() : {}", request.getRemoteAddr());

            String clientId = jwtContext.getJwtClaims().getSubject();
            ctx.set(CLIENT_ID_HEADER_NAME, clientId);
            // allows ClientIdMetricTagConfiguration to add the client id to metrics
            ctx.getRequest().setAttribute(CLIENT_ID_HEADER_NAME, clientId);
            zuulHeadersHelper.setAndEnable(ctx, CLIENT_ID_HEADER_NAME, clientId);
            return null;

        } catch (MalformedClaimException e) {
            // Happens when the token contains one or more malformed claims.
            log.error("JWT contains one or malformed claims to {} from IP {}",
                    requestURI, originIpName, e);
            semaEventLogger.logEvent(new MalformedJwtClaimSemaEvent(requestURI, originIpName));

            ZuulRequestRejecter.reject(ctx, ErrorConstants.INVALID_JWT, HttpStatus.BAD_REQUEST);
            return null;
        } catch (InvalidJwtException e) {
            // thrown by jwtConsumer.processToClaims. Anything can be wrong with the token.
            if (isExceptionDueToExpiredToken(e)) {
                log.info("Denying request because the access token is expired to {} from IP {}",
                        requestURI, originIpName, e);
                semaEventLogger.logEvent(new ExpiredJwtTokenSemaEvent(requestURI, originIpName));

                ZuulRequestRejecter.reject(ctx, ErrorConstants.EXPIRED_TOKEN, HttpStatus.UNAUTHORIZED);
                String authenticateHeader = "Bearer realm=\"yolt APIs\", error=\"invalid_token\", " +
                        "error_description=\"The access token is expired\"";
                ctx.addZuulResponseHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeader);
                return null;
            }

            log.error("Denying request because something is wrong with the token to {} from IP {}",
                    requestURI, originIpName, e);
            semaEventLogger.logEvent(new InvalidJwtTokenSemaEvent(requestURI, originIpName));
            ZuulRequestRejecter.reject(ctx, ErrorConstants.INVALID_JWT, HttpStatus.BAD_REQUEST);
            return null;
        }
    }

    boolean checkAccessTokenFormat(String accessToken, String originIpName, String requestURI) {
        if (!JWE_PATTERN.matcher(accessToken).matches()) {
            log.error("JWT does not conform to regex {} on uri {}, IP {}", JWE_PATTERN.pattern(), requestURI, originIpName);
            semaEventLogger.logEvent(new JwtTokenInvalidRegexSemaEvent(requestURI, originIpName));

            return false;
        } else if (accessToken.length() > MAX_ACCESS_TOKEN_LENGTH) {
            log.error("JWE Access token is bigger than the maximum size of {} chars, namely {} chars.",
                    MAX_ACCESS_TOKEN_LENGTH, accessToken.length());
            semaEventLogger.logEvent(new JwtTokenMaximumSizeExceededSemaEvent(requestURI, originIpName, MAX_ACCESS_TOKEN_LENGTH, accessToken.length()));

            return false;
        }
        return true;
    }

    private boolean isExceptionDueToExpiredToken(InvalidJwtException e) {
        // Exception handling could be better. Unfortunately, we have to get this from a message.
        return e.getMessage().contains("The JWT is no longer valid");
    }
}
