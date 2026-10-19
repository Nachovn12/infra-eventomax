package cl.duoc.eventomax.rabbitadmin.config;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
public class RabbitConfiguration {
    @Bean
    AmqpAdmin resourceAdmin(ConnectionFactory factory) {
        RabbitAdmin admin = new RabbitAdmin(factory);
        admin.setAutoStartup(false);
        admin.setIgnoreDeclarationExceptions(false);
        admin.setRedeclareManualDeclarations(false);
        return admin;
    }
}
