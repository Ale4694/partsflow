import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatPercent,
  formatQuantity,
  formatScore,
  formatWait,
} from './format';

/** Intl uses non-breaking spaces; compare with normal ones so the expectations stay readable. */
const plain = (text: string) => text.replace(/\s/g, ' ');

describe('format helpers (it-IT)', () => {
  describe('formatQuantity', () => {
    it('uses the comma as decimal separator and drops useless zeros', () => {
      expect(formatQuantity(6.5)).toBe('6,5');
      expect(formatQuantity(10)).toBe('10');
      expect(formatQuantity(0)).toBe('0');
    });

    it('keeps up to three decimals, like the backend', () => {
      expect(formatQuantity(1.2345)).toBe('1,235');
    });

    it('groups thousands with a dot from five digits on', () => {
      expect(formatQuantity(12345.5)).toBe('12.345,5');
    });

    it('shows a dash for a missing value', () => {
      expect(formatQuantity(null)).toBe('—');
      expect(formatQuantity(undefined)).toBe('—');
    });
  });

  describe('formatMoney', () => {
    it('shows euros with two decimals', () => {
      expect(plain(formatMoney(621.1))).toBe('621,10 €');
      expect(plain(formatMoney(1234.5))).toBe('1234,50 €');
      expect(plain(formatMoney(12345))).toBe('12.345,00 €');
    });

    it('shows a dash for a missing value', () => {
      expect(formatMoney(null)).toBe('—');
    });
  });

  describe('formatPercent', () => {
    it('shows the VAT rate with a percent sign', () => {
      expect(plain(formatPercent(22))).toBe('22%');
      expect(plain(formatPercent(5.5))).toBe('5,5%');
    });
  });

  describe('formatDate', () => {
    it('shows day/month/year', () => {
      expect(formatDate('2026-03-10')).toBe('10/03/2026');
    });

    it('does not shift a date without time to the previous day', () => {
      expect(formatDate('2026-01-01')).toBe('01/01/2026');
    });

    it('shows a dash for a missing value', () => {
      expect(formatDate(null)).toBe('—');
      expect(formatDate('')).toBe('—');
    });
  });

  describe('formatDateTime', () => {
    it('shows date and time of an instant', () => {
      const text = formatDateTime('2026-03-10T09:30:00Z');
      expect(text).toContain('10/03/2026');
      expect(text).toMatch(/\d{2}:\d{2}/);
    });

    it('shows a dash for a missing value', () => {
      expect(formatDateTime(undefined)).toBe('—');
    });
  });

  describe('formatWait', () => {
    it('uses seconds for short waits', () => {
      expect(formatWait(1)).toBe('1 secondo');
      expect(formatWait(33)).toBe('33 secondi');
      expect(formatWait(0)).toBe('1 secondo');
    });

    it('uses minutes and hours for longer waits', () => {
      expect(formatWait(90)).toBe('circa 2 minuti');
      expect(formatWait(12 * 60)).toBe('circa 12 minuti');
      expect(formatWait(32580)).toBe('circa 9 ore');
      expect(formatWait(3600)).toBe('circa 60 minuti');
      expect(formatWait(2 * 3600)).toBe('circa 2 ore');
    });
  });

  describe('formatScore', () => {
    it('shows the 0 to 1 score of a search result as a percentage', () => {
      expect(plain(formatScore(0.923))).toBe('92%');
      expect(plain(formatScore(1))).toBe('100%');
      expect(plain(formatScore(0.004))).toBe('0%');
    });

    it('shows a dash for a missing score', () => {
      expect(formatScore(null)).toBe('—');
    });
  });
});
