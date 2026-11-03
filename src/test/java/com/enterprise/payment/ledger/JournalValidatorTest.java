package com.enterprise.payment.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.enterprise.payment.ledger.domain.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalValidatorTest {
  @Test
  void acceptsBalancedJournal() {
    JournalValidator.validate(
        List.of(line(EntryType.DEBIT, "25.00"), line(EntryType.CREDIT, "25.00")));
  }

  @Test
  void rejectsUnbalancedJournal() {
    assertThrows(
        InvalidJournalException.class,
        () ->
            JournalValidator.validate(
                List.of(line(EntryType.DEBIT, "25.00"), line(EntryType.CREDIT, "24.99"))));
  }

  @Test
  void rejectsMixedCurrencies() {
    assertThrows(
        InvalidJournalException.class,
        () ->
            JournalValidator.validate(
                List.of(
                    new JournalLine(
                        UUID.randomUUID(), EntryType.DEBIT, new BigDecimal("10"), "MYR"),
                    new JournalLine(
                        UUID.randomUUID(), EntryType.CREDIT, new BigDecimal("10"), "USD"))));
  }

  @Test
  void rejectsNonPositiveAmount() {
    assertThrows(
        InvalidJournalException.class,
        () ->
            JournalValidator.validate(
                List.of(line(EntryType.DEBIT, "0"), line(EntryType.CREDIT, "0"))));
  }

  private JournalLine line(EntryType type, String amount) {
    return new JournalLine(UUID.randomUUID(), type, new BigDecimal(amount), "MYR");
  }
}
