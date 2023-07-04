package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.configuration.ZuulProperties;
import nl.ing.lovebird.clientproxy.controller.ErrorConstants;
import nl.ing.lovebird.clientproxy.controller.ZuulRequestRejecter;
import nl.ing.lovebird.clientproxy.exception.DestinationNotAvailableException;
import nl.ing.lovebird.clientproxy.exception.DestinationNotAvailableForMethodException;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.util.RequestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.AUTHENTICATION_REQUIRED;
import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_SET_DESTINATION_TYPE_FILTER;

@Component
@Slf4j
public class SetDestinationTypeFilter extends ZuulFilter {

    private static final PathMatcher pathMatcher = new AntPathMatcher();

    private final ZuulProperties zuulProperties;

    @Autowired
    public SetDestinationTypeFilter(ZuulProperties zuulProperties) {
        this.zuulProperties = zuulProperties;
    }

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_SET_DESTINATION_TYPE_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        return RequestContext.getCurrentContext().sendZuulResponse();
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();

        try {
            Boolean authenticationRequired = determineAuthenticationRequiredForPathAndMethod(ctx);

            ctx.set(AUTHENTICATION_REQUIRED, authenticationRequired);
            return authenticationRequired;
        } catch (DestinationNotAvailableException d) {
            log.error(d.getMessage());
            ZuulRequestRejecter.reject(ctx, ErrorConstants.DESTINATION_NOT_AVAILABLE, HttpStatus.NOT_FOUND);
        } catch (DestinationNotAvailableForMethodException d) {
            log.error(d.getMessage());
            ZuulRequestRejecter.reject(ctx, ErrorConstants.DESTINATION_NOT_AVAILABLE_FOR_METHOD, HttpStatus.METHOD_NOT_ALLOWED);
        }

        return null;
    }

    private boolean determineAuthenticationRequiredForPathAndMethod(RequestContext ctx) throws DestinationNotAvailableException, DestinationNotAvailableForMethodException {
        String proxyName = (String) ctx.get("proxy");
        String requestURI = (String) ctx.get("requestURI");

        ZuulProperties.ZuulRoute route = zuulProperties.getRoutes().get(proxyName);
        if (route == null) {
            throw new DestinationNotAvailableException(String.format("Route for proxy %s and requestURI %s is null", proxyName, requestURI));
        }

        ZuulProperties.ZuulSubPath subPath = determineSubPath(route, requestURI);
        if (subPath == null) {
            throw new DestinationNotAvailableException(String.format("SubPath for proxy %s and requestURI %s is null", proxyName, requestURI));
        }
        if (RequestUtils.isZuulServletRequest()) {
            // Zuul by default will copy all endpoints under /zuul/** because Spring's servlet is not handling big file uploads well.
            // Since we do not need that, and we do not want to unnecessarily expose extra endpoints, we can set it to empty to not take into effect.
            // 19.8 Uploading Files through Zuul -> https://shinley.gitbooks.io/spring-cloud/iii-spring-cloud-netflix/19.%20Router%20and%20Filter-Zuul.html
            // https://stackoverflow.com/questions/32084831/spring-cloud-netflix-whats-happening-in-zuulconfiguration-with-the-zuulservlet
            throw new DestinationNotAvailableException("Request to Zuul's servlet detected, which is not allowed until it's needed.");
        }

        final HttpServletRequest request = ctx.getRequest();
        final String method = request.getMethod();
        if (subPath.getOverrideHttpMethods() != null && subPath.getOverrideHttpMethods().contains(method)) {
            try {
                URL url = URI.create(subPath.getOverrideUrl()).toURL();
                ctx.setRouteHost(url);
            } catch (MalformedURLException e) {
                throw new DestinationNotAvailableException(String.format("SubPath's url for proxy %s and requestURI %s is invalid", proxyName, requestURI));
            }
        }

        if (subPath.getOpenHttpMethods() != null && subPath.getOpenHttpMethods().contains(method)) {
            return false;
        } else if (subPath.getAuthenticatedHttpMethods() != null && subPath.getAuthenticatedHttpMethods().contains(method)) {
            return true;
        } else {
            throw new DestinationNotAvailableForMethodException(request.getRequestURI(), request.getMethod());
        }
    }

    private ZuulProperties.ZuulSubPath determineSubPath(ZuulProperties.ZuulRoute route, String requestURI) {
        List<String> matchingPatterns = new ArrayList<>();
        // Find matching patterns: possible more than one applies
        for (ZuulProperties.ZuulSubPath subPath : route.getSubPaths()) {
            String pattern = subPath.getSubPath();
            if (pathMatcher.match(pattern, requestURI)) {
                matchingPatterns.add(pattern);
            }
        }
        if (matchingPatterns.isEmpty()) {
            // No matching patterns
            return null;
        } else if (matchingPatterns.size() == 1) {
            // Only one matching pattern, so use it
            return getMatchingSubPath(route, matchingPatterns.get(0));
        } else {
            // More than one pattern matches: use comparator to sort so that the pattern with the closest match will be used.
            Comparator<String> patternComparator = pathMatcher.getPatternComparator(requestURI);
            matchingPatterns.sort(patternComparator);
            return getMatchingSubPath(route, matchingPatterns.get(0));
        }
    }

    private static ZuulProperties.ZuulSubPath getMatchingSubPath(ZuulProperties.ZuulRoute route, String matchingPattern) {
        return route.getSubPaths().stream()
                .filter(subPath -> subPath.getSubPath().equalsIgnoreCase(matchingPattern))
                .findFirst()
                .orElse(null);
    }
}
