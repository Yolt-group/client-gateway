package nl.ing.lovebird.clientproxy.exception;

public class UnknownClientUserException extends RuntimeException {

    public UnknownClientUserException(String message) {
        super(message);
    }
}
