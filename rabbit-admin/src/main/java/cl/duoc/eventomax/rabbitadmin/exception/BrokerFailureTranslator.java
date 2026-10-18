package cl.duoc.eventomax.rabbitadmin.exception;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BrokerFailureTranslator {
    public int replyCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException shutdown) {
                if (shutdown.getReason() instanceof AMQP.Channel.Close close) return close.getReplyCode();
                if (shutdown.getReason() instanceof AMQP.Connection.Close close) return close.getReplyCode();
            }
        }
        return 0;
    }
    public AdminException translate(RuntimeException failure) {
        return switch (replyCode(failure)) {
            case 403, 530 -> new AdminException(HttpStatus.FORBIDDEN, "Broker permissions do not allow this operation");
            case 404 -> new AdminException(HttpStatus.NOT_FOUND, "Required broker resource does not exist");
            case 405, 406 -> new AdminException(HttpStatus.CONFLICT, "Resource is incompatible, in use, or not empty");
            case 502, 503, 504, 505, 506 -> new AdminException(HttpStatus.BAD_GATEWAY, "Broker rejected the AMQP operation");
            default -> new AdminException(HttpStatus.SERVICE_UNAVAILABLE, "Broker operation unavailable; outcome may be unknown. Reconcile before retrying");
        };
    }
}
