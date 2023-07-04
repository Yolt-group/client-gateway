package nl.ing.lovebird.clientproxy.exception;

public class InvalidJwtConsumerConfigurationException extends RuntimeException {
    public InvalidJwtConsumerConfigurationException(Exception cause) {
        super(cause);
    }
}
