package cl.duoc.eventomax.rabbitadmin.controller;
import cl.duoc.eventomax.rabbitadmin.dto.*;
import cl.duoc.eventomax.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rabbit-admin")
public class RabbitAdminController {
    private final RabbitAdminService service;
    public RabbitAdminController(RabbitAdminService service) { this.service = service; }
    @PostMapping("/queues")
    public DeclarationResponse createQueue(@Valid @RequestBody QueueRequest request) { return service.createQueue(request); }
    @GetMapping("/queues/{name}")
    public QueueResponse getQueue(@PathVariable String name) { return service.getQueue(name); }
    @DeleteMapping("/queues/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable String name) { service.deleteQueue(name); }
    @PostMapping("/exchanges")
    public DeclarationResponse createExchange(@Valid @RequestBody ExchangeRequest request) { return service.createExchange(request); }
    @DeleteMapping("/exchanges/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable String name) { service.deleteExchange(name); }
    @PostMapping("/bindings") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createBinding(@Valid @RequestBody BindingRequest request) { service.createBinding(request); }
    @DeleteMapping("/bindings") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@Valid @RequestBody BindingRequest request) { service.deleteBinding(request); }
}
