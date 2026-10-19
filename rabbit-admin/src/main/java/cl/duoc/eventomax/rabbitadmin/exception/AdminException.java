package cl.duoc.eventomax.rabbitadmin.exception;
import org.springframework.http.HttpStatus;
public class AdminException extends RuntimeException {
    private final HttpStatus status;
    public AdminException(HttpStatus status, String detail) { super(detail); this.status = status; }
    public HttpStatus status() { return status; }
}
