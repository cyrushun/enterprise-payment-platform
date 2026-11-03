package com.enterprise.payment.ledger.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Enforces journal invariants before a journal can be persisted as POSTED. */
public final class JournalValidator {
  private JournalValidator() {}

  public static void validate(List<JournalLine> lines) {
    if (lines == null || lines.size() < 2)
      throw new InvalidJournalException("A journal must contain at least two entries");
    JournalLine first = lines.getFirst();
    if (first == null || first.currency() == null)
      throw new InvalidJournalException("Journal entries must be complete");
    String currency = first.currency();
    BigDecimal debits = BigDecimal.ZERO;
    BigDecimal credits = BigDecimal.ZERO;
    for (JournalLine line : lines) {
      if (line == null
          || line.accountId() == null
          || line.type() == null
          || line.amount() == null
          || line.currency() == null)
        throw new InvalidJournalException("Journal entries must be complete");
      if (line.amount().signum() <= 0)
        throw new InvalidJournalException("Journal entry amounts must be positive");
      if (line.amount().scale() > 4 || line.amount().precision() > 19)
        throw new InvalidJournalException("Journal amounts must fit NUMERIC(19,4)");
      if (!line.currency().matches("[A-Z]{3}"))
        throw new InvalidJournalException("Currency must be a 3-letter uppercase code");
      if (!Objects.equals(currency, line.currency()))
        throw new InvalidJournalException("A journal cannot mix currencies");
      if (line.type() == EntryType.DEBIT) debits = debits.add(line.amount());
      else credits = credits.add(line.amount());
    }
    if (debits.compareTo(credits) != 0)
      throw new InvalidJournalException("Journal debits must equal credits");
  }
}
