package nl.ing.lovebird.clientproxy.service;

import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.exception.ClientUserBlockedException;
import nl.ing.lovebird.clientproxy.exception.UnknownClientUserException;
import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class ClientUserService {

    private final ClientUsersClient clientUsersClient;

    @Autowired
    public ClientUserService(ClientUsersClient clientUsersClient) {
        this.clientUsersClient = clientUsersClient;
    }

    public UserDTO getUser(UUID clientId, UUID clientUserId, UUID requestTraceId) {
        return clientUsersClient.getClientUser(clientId, clientUserId, requestTraceId)
                .map(cu -> {
                    if (cu.isBlocked()) {
                        throw new ClientUserBlockedException(clientId, clientUserId, cu.getUserId());
                    }
                    return cu;
                })
                .orElseThrow(() -> new UnknownClientUserException("No clientUser found for clientId = " + clientId + " and clientUserId = " + clientUserId.toString()));
    }
}
