# 0002. BigDecimal, created from strings, for money and quantities

## Context

Invoices are about money (`PrezzoTotale`, `Imposta`) and quantities (`Quantita`), and the system checks that line totals add up to the declared totals. `double` and `float` store numbers in binary, so `0.1 + 0.2` is `0.30000000000000004`. Small errors like that would make correct invoices fail the totals check, or make stock drift by a fraction.

A second trap: `new BigDecimal(0.1)` converts the *already inexact* double, giving `0.1000000000000000055511151231257827...`.

## Decision

- Money, prices, VAT rates and quantities are always `BigDecimal` in Java and `NUMERIC` in PostgreSQL. `double` and `float` are never used for them.
- A `BigDecimal` is always created **from a string** (`new BigDecimal("25.00")`), never from a `double`. The XML mapping classes keep every value as a `String` on purpose; the mapper parses it, so a bad value produces a clear error message ("Invalid number in Quantita: 'ten'") instead of a silent wrong number.
- Comparisons use `compareTo`, never `equals` (`new BigDecimal("2.0").equals(new BigDecimal("2.00"))` is `false`).
- Rounding is explicit where it happens (`RoundingMode.HALF_UP` for VAT amounts); nothing is rounded silently. A quantity with more than 3 decimals is rejected with a 422 instead of being rounded when stored.
- The totals check allows a difference of 0.01 per VAT rate, because the FatturaPA standard itself allows one cent of rounding.
- The LLM also returns numbers as strings, which our code parses and validates like any other input.

## Alternatives considered

- **`double`**: simple, fast, wrong for money.
- **Integer cents (`long`)**: exact and fast, but FatturaPA prices have up to 8 decimals and quantities have decimals too, so a fixed "cents" scale does not fit.
- **A `Money` value object** (amount + currency): good in a multi-currency system. Here everything is EUR, so it would add ceremony without adding safety. Non-EUR documents are rejected instead.

## Consequences

- Calculations are exact and the totals validation is trustworthy.
- `BigDecimal` code is more verbose (`a.add(b)` instead of `a + b`) and has pitfalls (scale, `equals`) that the team has to know. Tests use `isEqualByComparingTo` for that reason.
