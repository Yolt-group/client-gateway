package nl.ing.lovebird.clientproxy.filter.filters.pre.sema;

import lombok.AllArgsConstructor;
import net.logstash.logback.marker.Markers;
import nl.ing.lovebird.logging.SemaEvent;
import org.slf4j.Marker;

@AllArgsConstructor
public class JwtTokenMaximumSizeExceededSemaEvent implements SemaEvent {

    private final String requestURI;
    private final String remoteAddress;
    private final int maximumSize;
    private final int actualSize;

    @Override
    public String getMessage() {
        return String.format("JWT is bigger than allowed (%s > %s characters) on uri %s, IP %s",
                actualSize, maximumSize, requestURI, remoteAddress);
    }

    @Override
    public Marker getMarkers() {
        return Markers.append("requestURI", requestURI)
                .and(Markers.append("remoteAddress", remoteAddress));
    }
}
