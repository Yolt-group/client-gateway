package nl.ing.lovebird.clientproxy.filter.filters.pre;

import brave.Tracer;
import brave.baggage.BaggageField;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.controller.ErrorConstants;
import nl.ing.lovebird.clientproxy.controller.ZuulRequestRejecter;
import nl.ing.lovebird.clientproxy.exception.ClientUserBlockedException;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clientproxy.service.ClientUserService;
import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.requester.service.ClientTokenRequesterService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClientUserToUserMappingFilter extends ZuulFilter {

    private final BaggageField SLEUTH_BAGGAGE_CLIENT_ID = BaggageField.create(CLIENT_ID_HEADER_NAME);

    private static final Pattern HAS_USER_ID_IN_PATH_PATTERN = Pattern.compile("(/v\\d+)?/users/([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})(/.*)*");

    private final Optional<Tracer> maybeTracer;
    private final ClientUserService clientUserService;
    private final ZuulHeadersHelper zuulHeadersHelper;
    private final ClientTokenRequesterService clientTokenRequesterService;

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_PROFILE_TO_USER_MAPPING_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();

        String clientUserId = ctx.getRequest().getHeader(CLIENT_USER_ID_HEADER_NAME);
        boolean hasUserIdInPath = HAS_USER_ID_IN_PATH_PATTERN.matcher(ctx.getRequest().getRequestURI()).matches();

        String clientId = ctx.getZuulRequestHeaders().get(CLIENT_ID_HEADER_NAME);

        return ctx.sendZuulResponse() && !StringUtils.isEmpty(clientId) && (!StringUtils.isEmpty(clientUserId) || hasUserIdInPath);
    }

    @SuppressWarnings("squid:S3516")
    // The method always returns null, because Zuul allows any arbitrary data to be returned, while not using it.. (see javadoc)
    @Override
    public Object run() {
        final RequestContext ctx = RequestContext.getCurrentContext();

        final UUID requestTraceId = UUID.fromString(ctx.getZuulRequestHeaders().get(REQUEST_TRACE_ID_HEADER_NAME));

        final String clientId = (String) ctx.get(CLIENT_ID_HEADER_NAME);
        Matcher matcher = HAS_USER_ID_IN_PATH_PATTERN.matcher(ctx.getRequest().getRequestURI());
        boolean hasUserIdInPath = matcher.find();
        final String clientUserId;
        if (hasUserIdInPath) {
            clientUserId = matcher.group(2);
        } else {
            clientUserId = ctx.getRequest().getHeader(CLIENT_USER_ID_HEADER_NAME);
        }
        final UUID clientUUID = UUID.fromString(clientId);
        final UUID clientUserUUID;
        try {
            clientUserUUID = UUID.fromString(clientUserId);
        } catch (IllegalArgumentException e) {
            ZuulRequestRejecter.reject(ctx, ErrorConstants.INCORRECT_CLIENT_USER_ID_FORMAT, HttpStatus.BAD_REQUEST);
            return null;
        }

        final UserDTO userDetails;
        try {
            userDetails = clientUserService.getUser(clientUUID, clientUserUUID, requestTraceId);
        } catch (ClientUserBlockedException ex) {
            ZuulRequestRejecter.reject(ctx, ErrorConstants.BLOCKED_CLIENT_USER, HttpStatus.LOCKED);
            return null;
        }

        ClientUserToken clientuserToken = clientTokenRequesterService.getClientUserToken(UUID.fromString(clientId), userDetails.getUserId());

        if (hasUserIdInPath) {
            ctx.put("requestURI", ((String) ctx.get("requestURI")).replace(clientUserId, userDetails.getUserId().toString()));
        }

        ctx.set(CLIENT_TOKEN_HEADER_NAME, clientuserToken);
        zuulHeadersHelper.setAndEnable(ctx, CLIENT_TOKEN_HEADER_NAME, clientuserToken.getSerialized());
        zuulHeadersHelper.setAndEnable(ctx, USER_ID_HEADER_NAME, clientuserToken.getUserIdClaim().toString());
        zuulHeadersHelper.setAndEnable(ctx, CLIENT_USER_ID_HEADER_NAME, clientuserToken.getClientUserIdClaim().toString());

        // The client-id *must* be propagated as Sleuth Baggage (header: baggage-client-id)
        maybeTracer
                .flatMap(tracer -> Optional.ofNullable(tracer.currentSpan()))
                .ifPresentOrElse(
                        currentSpan -> SLEUTH_BAGGAGE_CLIENT_ID.updateValue(currentSpan.context(), clientUUID.toString()),
                        () -> log.warn("Tracer and/or current span context not available."));

        return null;
    }

}
