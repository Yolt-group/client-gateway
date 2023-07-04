package nl.ing.lovebird.clientproxy.service.caching;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode
public class CachedUserDetailsKey {
    private final UUID clientId;
    private final UUID clientUserId;
}
