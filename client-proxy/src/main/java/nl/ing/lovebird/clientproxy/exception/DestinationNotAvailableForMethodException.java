package nl.ing.lovebird.clientproxy.exception;

public class DestinationNotAvailableForMethodException extends Exception {
    public DestinationNotAvailableForMethodException(final String requestURI, final String method) {
        super(String.format("URI %s with method %s is not available", requestURI, method));
    }
}
