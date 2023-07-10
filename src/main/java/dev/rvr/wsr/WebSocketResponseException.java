package dev.rvr.wsr;
import lombok.Getter;

/**
 * An exception that is thrown when the server responds with an error.
 */
@Getter
public class WebSocketResponseException extends RuntimeException {
    private final int errno;
    WebSocketResponseException(String message, int errno) {
        super(message);
        this.errno = errno;
    }
}
