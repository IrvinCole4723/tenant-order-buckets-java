package learning.store.http;

import learning.store.orders.CheckoutRequest;
import learning.store.orders.OrderArchiveResult;
import learning.store.orders.OrderArchiveService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public final class OrderArchiveController {
    private final OrderArchiveService service;

    public OrderArchiveController(OrderArchiveService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public OrderArchiveResult checkout(@RequestBody CheckoutRequest request) {
        try {
            return service.archiveCheckout(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
