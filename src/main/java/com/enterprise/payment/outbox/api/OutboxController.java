package com.enterprise.payment.outbox.api;

import com.enterprise.payment.outbox.application.OutboxService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/outbox/events")
public class OutboxController {
  private final OutboxService service;

  public OutboxController(OutboxService s) {
    service = s;
  }

  @GetMapping
  public Page<OutboxEventResponse> list(Pageable p) {
    return service.list(p).map(OutboxEventResponse::from);
  }

  @PostMapping("/{id}/replay")
  public OutboxEventResponse replay(@PathVariable UUID id) {
    return OutboxEventResponse.from(service.replay(id));
  }

  @GetMapping("/{id}")
  public OutboxEventResponse get(@PathVariable UUID id) {
    return OutboxEventResponse.from(service.get(id));
  }
}
