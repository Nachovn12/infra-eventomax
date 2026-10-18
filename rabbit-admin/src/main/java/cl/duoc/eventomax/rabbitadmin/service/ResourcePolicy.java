package cl.duoc.eventomax.rabbitadmin.service;
import cl.duoc.eventomax.rabbitadmin.exception.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ResourcePolicy {
    public void requireDemo(String name) {
        if (name == null || name.isBlank() || name.length() > 128
                || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "Invalid resource name");
        }
        if (!name.startsWith("demo.")) {
            throw new AdminException(HttpStatus.FORBIDDEN, "Only resources in the demo. namespace may be administered");
        }
        if (!name.matches("demo\\.[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "Invalid resource name");
        }
    }
}

