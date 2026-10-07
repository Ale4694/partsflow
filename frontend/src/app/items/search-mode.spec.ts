import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchFallbackReason, SearchMode } from '../core/models';
import { SearchModeIndicator } from './search-mode';

describe('SearchModeIndicator', () => {
  let fixture: ComponentFixture<SearchModeIndicator>;

  async function show(mode: SearchMode, reason: SearchFallbackReason | null = null): Promise<string> {
    fixture = TestBed.createComponent(SearchModeIndicator);
    fixture.componentRef.setInput('mode', mode);
    fixture.componentRef.setInput('fallbackReason', reason);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('says "ricerca intelligente" for a hybrid search', async () => {
    const text = await show('HYBRID');

    expect(text).toContain('ricerca intelligente');
    expect(text).not.toContain('ricerca testuale');
  });

  it('says "ricerca testuale" for a text search that was asked for', async () => {
    const text = await show('TEXT');

    expect(text).toContain('ricerca testuale');
    expect(text).not.toContain('ricerca intelligente');
  });

  it.each([
    ['NOT_CONFIGURED', 'non è configurata'],
    ['DIMENSION_MISMATCH', 'dimensione degli embedding'],
    ['NOT_INDEXED', 'non è stato indicizzato'],
    ['PROVIDER_ERROR', 'senza quota'],
  ] as [SearchFallbackReason, string][])('explains the fallback %s', async (reason, expected) => {
    const text = await show('TEXT', reason);

    expect(text).toContain('ricerca testuale');
    expect(text).toContain(expected);
  });
});
