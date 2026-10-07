import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SEARCH_DEBOUNCE_MS } from '../core/debounce';
import { ItemSearchResult, PickedItem } from '../core/models';
import { ItemPicker } from './item-picker';

const BROWSE_PAGE = {
  content: [{ id: 1, code: 'BRK-001', description: 'Pastiglie freni', unit: 'PZ', reorderThreshold: 5 }],
  page: 0,
  size: 8,
  totalElements: 1,
  totalPages: 1,
};

function hit(itemId: number, code: string, description: string, score: number) {
  return { itemId, code, description, unit: 'PZ', reorderThreshold: 0, quantity: 0, score, vectorScore: score, textScore: null };
}

describe('ItemPicker', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<ItemPicker>;
  let picked: PickedItem[];

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const DEBOUNCE = 40;
  const wait = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

  /** Types in the search box and lets the debounce time pass. */
  async function type(value: string): Promise<void> {
    const box = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
    box.value = value;
    box.dispatchEvent(new Event('input'));
    await wait(DEBOUNCE + 40);
    fixture.detectChanges();
  }

  async function answerSearch(result: ItemSearchResult): Promise<void> {
    backend.expectOne((r) => r.url === '/api/items/search').flush(result);
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SEARCH_DEBOUNCE_MS, useValue: DEBOUNCE }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemPicker);
    picked = [];
    fixture.componentInstance.picked.subscribe((item) => picked.push(item));
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/items').flush(BROWSE_PAGE);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows the first items before anything is typed, with no search indicator', () => {
    expect(text()).toContain('BRK-001');
    expect(text()).not.toContain('ricerca');
  });

  it('waits until the user stops typing before it searches (one request per pause)', async () => {
    const box = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
    for (const partial of ['f', 'fi', 'filtro']) {
      box.value = partial;
      box.dispatchEvent(new Event('input')); // typing faster than the pause
    }
    backend.expectNone((r) => r.url === '/api/items/search');

    await wait(DEBOUNCE + 40);
    fixture.detectChanges();

    const request = backend.expectOne((r) => r.url === '/api/items/search');
    expect(request.request.params.get('q')).toBe('filtro');
    request.flush({ mode: 'HYBRID', fallbackReason: null, results: [] });
  });

  it('shows the results with their score and "ricerca intelligente" for a hybrid search', async () => {
    await type('filtro olio Fiat Panda');
    await answerSearch({
      mode: 'HYBRID',
      fallbackReason: null,
      results: [hit(7, 'CTL-1', 'Cartuccia lubrificante motore 1.2 FIRE', 0.92)],
    });

    expect(text()).toContain('ricerca intelligente');
    expect(text()).toContain('Cartuccia lubrificante motore 1.2 FIRE');
    expect(text()).toContain('92');
  });

  it('shows "ricerca testuale" and why when the smart search was not available', async () => {
    await type('filtro');
    await answerSearch({
      mode: 'TEXT',
      fallbackReason: 'PROVIDER_ERROR',
      results: [hit(8, 'CTL-2', 'Filtro aria abitacolo', 0.5)],
    });

    expect(text()).toContain('ricerca testuale');
    expect(text()).toContain('senza quota');
    expect(text()).toContain('Filtro aria abitacolo');
  });

  it('says so when nothing matches', async () => {
    await type('qqzzxx');
    await answerSearch({ mode: 'HYBRID', fallbackReason: null, results: [] });

    expect(text()).toContain('Nessun articolo trovato.');
  });

  it('hands over the chosen item', async () => {
    await type('filtro');
    await answerSearch({ mode: 'HYBRID', fallbackReason: null, results: [hit(8, 'CTL-2', 'Filtro aria abitacolo', 0.5)] });

    const button = (fixture.nativeElement as HTMLElement).querySelector('li button') as HTMLButtonElement;
    button.click();

    expect(picked).toEqual([{ id: 8, code: 'CTL-2', description: 'Filtro aria abitacolo', unit: 'PZ', score: 0.5 }]);
  });

  it('goes back to the first items when the box is emptied', async () => {
    await type('filtro');
    await answerSearch({ mode: 'HYBRID', fallbackReason: null, results: [] });

    await type('');

    expect(text()).toContain('BRK-001');
    expect(text()).not.toContain('ricerca intelligente');
  });
});
