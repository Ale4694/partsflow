import { Pipe, PipeTransform } from '@angular/core';

// Display formatting only (it-IT). The frontend never calculates with money or quantities: the backend does.

const EMPTY = '—';

const quantityFormat = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 3 });
const moneyFormat = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' });
const percentFormat = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 2 });
const dateOptions: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' };
// A date without time ("2026-03-10") is read as UTC midnight, so it is also shown in UTC: no off-by-one day
const dateFormat = new Intl.DateTimeFormat('it-IT', { ...dateOptions, timeZone: 'UTC' });
const dateTimeFormat = new Intl.DateTimeFormat('it-IT', { ...dateOptions, hour: '2-digit', minute: '2-digit' });

export function formatQuantity(value: number | null | undefined): string {
  return value == null ? EMPTY : quantityFormat.format(value);
}

export function formatMoney(value: number | null | undefined): string {
  return value == null ? EMPTY : moneyFormat.format(value);
}

export function formatPercent(value: number | null | undefined): string {
  return value == null ? EMPTY : percentFormat.format(value) + '%';
}

/** A date without time, as sent by the backend ("2026-03-10"). */
export function formatDate(value: string | null | undefined): string {
  return value ? dateFormat.format(new Date(value)) : EMPTY;
}

/** An instant, as sent by the backend ("2026-03-10T09:30:00Z"), shown in the browser's time zone. */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTimeFormat.format(new Date(value)) : EMPTY;
}

/** A wait for people: "circa 33 secondi", "circa 12 minuti", "circa 9 ore". */
export function formatWait(seconds: number): string {
  const rounded = Math.max(1, Math.round(seconds));
  if (rounded < 90) {
    return rounded === 1 ? '1 secondo' : `${rounded} secondi`;
  }
  const minutes = Math.round(rounded / 60);
  if (minutes < 90) {
    return `circa ${minutes} minuti`;
  }
  const hours = Math.round(minutes / 60);
  return hours === 1 ? 'circa 1 ora' : `circa ${hours} ore`;
}

@Pipe({ name: 'quantity' })
export class QuantityPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatQuantity(value);
  }
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatMoney(value);
  }
}

@Pipe({ name: 'percent2' })
export class PercentPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatPercent(value);
  }
}

@Pipe({ name: 'itDate' })
export class ItDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDate(value);
  }
}

@Pipe({ name: 'itDateTime' })
export class ItDateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDateTime(value);
  }
}
