package nl.ing.lovebird.clientproxy.service;

import nl.ing.lovebird.clientproxy.exception.ClientUserBlockedException;
import nl.ing.lovebird.clientproxy.exception.UnknownClientUserException;
import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientUserServiceTest {

    private ClientUserService clientUserService;

    @Mock
    private ClientUsersClient clientUsersClient;

    @BeforeEach
    void setup() {
        clientUserService = new ClientUserService(clientUsersClient);
    }

    @Test
    void shouldGetUserId() {
        UUID clientUUID = UUID.randomUUID();
        UUID clientUserUUID = UUID.randomUUID();
        UUID userUUID = UUID.randomUUID();

        UserDTO clientUserDTO = new UserDTO(clientUUID, clientUserUUID, userUUID, false, null, null, null, false, LocalDateTime.now(), null);

        when(clientUsersClient.getClientUser(any(), any(), any())).thenReturn(Optional.of(clientUserDTO));
        UserDTO user = clientUserService.getUser(clientUUID, clientUserUUID, UUID.randomUUID());
        assertEquals(userUUID.toString(), user.getUserId().toString());
    }

    @Test
    void shouldThrowExceptionOnUnknownClientUser() {
        when(clientUsersClient.getClientUser(any(), any(), any())).thenReturn(Optional.empty());
        assertThrows(UnknownClientUserException.class, () -> clientUserService.getUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void shouldThrowExceptionOnBlockedClientUser() {
        UUID clientId = UUID.randomUUID();
        UUID clientUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserDTO clientUserDTO = new UserDTO(clientId, clientUserId, userId, true, "System", "reasons", LocalDateTime.now(), false, LocalDateTime.now(), null);
        when(clientUsersClient.getClientUser(eq(clientId), eq(clientUserId), any())).thenReturn(Optional.of(clientUserDTO));
        assertThrows(ClientUserBlockedException.class, () -> clientUserService.getUser(clientId, clientUserId, UUID.randomUUID()));
    }
}
