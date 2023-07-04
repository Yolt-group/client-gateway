package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clientproxy.service.kafka.ClientCertificateEventProducer;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_MTLS_FILTER;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;

@Component
@Slf4j
@RequiredArgsConstructor
public class MTLSCertificateLoggingFilter extends ZuulFilter {

    private final ClientCertificateEventProducer clientCertificateEventProducer;
    private final Clock clock;

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        return ctx.sendZuulResponse() && ctx.containsKey(CLIENT_TOKEN_HEADER_NAME);
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_MTLS_FILTER;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        ClientToken clientToken = getClientToken(ctx);
        if (clientToken != null) {
            getClientCertificate(clientToken, ctx).ifPresent(clientCertificateDTO -> clientCertificateEventProducer.sendMessage(clientToken, clientCertificateDTO));
        }

        return null;
    }

    private ClientToken getClientToken(RequestContext ctx) {
        if (ctx.containsKey(CLIENT_TOKEN_HEADER_NAME)) {
            return (ClientToken) ctx.get(CLIENT_TOKEN_HEADER_NAME);
        }
        return null;
    }

    private Optional<ClientCertificateDTO> getClientCertificate(ClientToken clientToken, RequestContext ctx) {
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
                clientToken.getClientIdClaim(),
                sslClientFingerprint,
                URLDecoder.decode(sslClientCertificate, requestCharSet(request)),
                LocalDateTime.now(clock)
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
