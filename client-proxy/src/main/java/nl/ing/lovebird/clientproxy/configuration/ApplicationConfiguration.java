package nl.ing.lovebird.clientproxy.configuration;

import nl.ing.lovebird.clientproxy.filter.filters.pre.AccessTokenValidationFilter;
import nl.ing.lovebird.clientproxy.filter.filters.pre.ZuulHeadersHelper;
import nl.ing.lovebird.logging.SemaEventLogger;
import nl.ing.lovebird.secretspipeline.VaultKeys;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.security.Security;
import java.time.Clock;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties({ZuulProperties.class})
public class ApplicationConfiguration {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final String CLIENT_ID_HEADER_NAME = "client-id";
    public static final String CLIENT_TOKEN_HEADER_NAME = "client-token";
    public static final String REQUEST_TRACE_ID_HEADER_NAME = "request_trace_id";
    public static final String CLIENT_USER_ID_HEADER_NAME = "client-user-id";
    public static final String USER_ID_HEADER_NAME = "user-id";
    public static final String AUTHENTICATION_REQUIRED = "authentication-required";

    public static final int FILTER_ORDER_REQUEST_TRACE_ID_FILTER = 1;
    // 5 reserved for org.springframework.cloud.netflix.zuul.filters.pre.PreDecorationFilter
    public static final int FILTER_ORDER_SET_DESTINATION_TYPE_FILTER = 6;
    public static final int FILTER_ORDER_REMOVE_PROXY_FORWARDED_PREFIX = 7;

    public static final int FILTER_ORDER_ACCESSTOKEN_FILTER = 7;
    public static final int FILTER_ORDER_CLIENT_USERS_KYC_FILTER = 8;
    public static final int FILTER_ORDER_PROFILE_TO_USER_MAPPING_FILTER = 10;
    public static final int FILTER_ORDER_CLIENTTOKEN_FILTER = 11;
    public static final int FILTER_ORDER_MTLS_FILTER = 12;

    public static final int FILTER_ORDER_LOGGING_FILTER = 21;

    @Bean
    public AccessTokenValidationFilter accessTokenValidationFilter(
            VaultKeys vaultKeys,
            ZuulHeadersHelper zuulHeadersHelper,
            SemaEventLogger semaEventLogger
    ) {
        return new AccessTokenValidationFilter(
                new String(vaultKeys.getPassword("cgw-tokens-encryption").getPassword()),
                zuulHeadersHelper,
                semaEventLogger);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
