package com.enterprise.payment.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
  public static void main(String[] args) {
    var encoder = new BCryptPasswordEncoder();

    System.out.println(encoder.encode("OperationsTest123!"));

    System.out.println(encoder.encode("AdminTest123!"));
  }
}
