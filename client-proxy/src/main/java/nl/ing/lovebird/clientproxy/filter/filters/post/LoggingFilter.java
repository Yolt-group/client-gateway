package nl.ing.lovebird.clientproxy.filter.filters.post;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clientproxy.service.ApiRequest;
import nl.ing.lovebird.clientproxy.service.kafka.IncomingRequestEventProducer;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME;
import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_LOGGING_FILTER;

@Component
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class LoggingFilter extends ZuulFilter {

    private static final Clock DEFAULT_CLOCK = Clock.systemUTC();

    @Override
    public String filterType() {
        return FilterType.POST.getType();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_LOGGING_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Autowired
    private IncomingRequestEventProducer incomingRequestEventProducer;



    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();

        String requestURI = RequestContext.getCurrentContext().getRequest().getRequestURI();
        String routeHost = "NO_ROUTE";
        Integer responseStatus = null;
        String uri = ctx.get("requestURI") != null ? ctx.get("requestURI").toString() : "";
        String method = ctx.getRequest().getMethod();

        if (ctx.get("routeHost") instanceof URL) {
            routeHost = ctx.get("routeHost").toString();
        }
        if (ctx.get("responseStatusCode") instanceof Integer) {

            responseStatus = (Integer) ctx.get("responseStatusCode");
        }

        log.info("Request URI : {}, Routed to: {}, Response status : {},  Filter summary {}",
                requestURI,
                routeHost + uri,
                responseStatus != null ? responseStatus.toString() : "NO_RESPONSE",
                ctx.getFilterExecutionSummary().toString());

        try {
            UUID clientId = null;
            UUID clientGroupId = null;
            UUID clientUserId = null;
            UUID userId = null;
            Optional<ClientCertificateDTO> clientCertificate = Optional.empty();
            ClientToken clientToken = (ClientToken) ctx.get(CLIENT_TOKEN_HEADER_NAME);
            if (clientToken != null) {
                clientCertificate = getClientCertificate(ctx);
                clientId = clientToken.getClientIdClaim();
                clientGroupId = clientToken.getClientGroupIdClaim();
                if (clientToken instanceof ClientUserToken) {
                    clientUserId = ((ClientUserToken) clientToken).getClientUserIdClaim();
                    userId = ((ClientUserToken) clientToken).getUserIdClaim();
                }
            }

            if (clientId != null && clientGroupId != null) {

                ApiRequest apiRequest = ApiRequest.builder()
                        .clientGroupId(clientGroupId)
                        .clientId(clientId)
                        .clientUserId(clientUserId)
                        .userId(userId)
                        .method(method)
                        .endpoint(requestURI)
                        .httpResponseCode(responseStatus)
                        .loggedAt(LocalDateTime.now(DEFAULT_CLOCK))
                        .clientCertificateDTO(clientCertificate.orElse(null))
                        .build();

                incomingRequestEventProducer.sendMessage(apiRequest);
            }
        } catch (Exception ex) {
            log.error("Exception occured when trying to send api request to Kafka", ex);
        }
        return null;
    }

    private Optional<ClientCertificateDTO> getClientCertificate(RequestContext ctx) {
        HttpServletRequest request = ctx.getRequest();
        var sslClientFingerprint = request.getHeader("X-SSL-Client-FINGERPRINT");
        if (sslClientFingerprint == null) {
            return Optional.empty();
        }
        var sslClientCertificate = request.getHeader("X-SSL-Client-CERTIFICATE");
        if (sslClientCertificate == null) {
            return Optional.empty();
        }

        return Optional.of(new ClientCertificateDTO(
                null,
                sslClientFingerprint,
                URLDecoder.decode(sslClientCertificate, requestCharSet(request)),
                LocalDateTime.now(DEFAULT_CLOCK)
        ));
    }

    private Charset requestCharSet(HttpServletRequest request) {
        String requestCharSet = request.getCharacterEncoding();
        if (requestCharSet != null) {
            try {
                return Charset.forName(requestCharSet);
            } catch (UnsupportedCharsetException e) {
                log.debug("could not get charset from request", e);
            }
        }
        String servletCharset = request.getServletContext().getRequestCharacterEncoding();
        if (servletCharset != null) {
            try {
                return Charset.forName(servletCharset);
            } catch (UnsupportedCharsetException e) {
                log.debug("could not get charset from servlet", e);
            }
        }

        return StandardCharsets.ISO_8859_1;
    }
}
