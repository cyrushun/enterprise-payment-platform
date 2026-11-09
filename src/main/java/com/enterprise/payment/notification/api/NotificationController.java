package com.enterprise.payment.notification.api;

import com.enterprise.payment.notification.application.NotificationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService s) {
    service = s;
  }

  @GetMapping
  public Page<NotificationResponse> list(@RequestParam UUID accountId, Pageable pageable) {
    return service.list(accountId, pageable).map(NotificationResponse::from);
  }
}
