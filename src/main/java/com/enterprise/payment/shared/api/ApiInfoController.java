package com.enterprise.payment.shared.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiInfoController {

  @GetMapping("/api/v1")
  public Map<String, String> apiInfo() {
    return Map.of(
        "name", "Enterprise Payment Platform",
        "version", "v1",
        "status", "FOUNDATION");
  }
}
