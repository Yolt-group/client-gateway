package nl.ing.lovebird.clientproxy.filter.filters.pre.sema;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import net.logstash.logback.marker.Markers;
import nl.ing.lovebird.logging.SemaEvent;
import org.slf4j.Marker;

@AllArgsConstructor
@EqualsAndHashCode
public class JwtTokenInvalidRegexSemaEvent implements SemaEvent {

    private final String requestURI;

    private final String remoteAddress;

    @Override
    public String getMessage() {
        return String.format("JWT does not conform to regex on uri %s, IP %s", requestURI, remoteAddress);
    }

    @Override
    public Marker getMarkers() {
        return Markers.append("requestURI", requestURI)
                .and(Markers.append("remoteAddress", remoteAddress));
    }
}
