import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SEARCH_DEBOUNCE_MS } from '../core/debounce';
import { ItemList } from './item-list';

const STOCK_PAGE = {
  content: [{ itemId: 1, itemCode: 'BRK-001', description: 'Pastiglie freni', unit: 'PZ', quantity: 4, reorderThreshold: 5 }],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('ItemList search', () => {
  const DEBOUNCE = 40;
  let backend: HttpTestingController;
  let fixture: ComponentFixture<ItemList>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const wait = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

  async function search(value: string): Promise<void> {
    const box = (fixture.nativeElement as HTMLElement).querySelector('#item-search') as HTMLInputElement;
    box.value = value;
    box.dispatchEvent(new Event('input'));
    await wait(DEBOUNCE + 40);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SEARCH_DEBOUNCE_MS, useValue: DEBOUNCE }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemList);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/inventory/stock').flush(STOCK_PAGE);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows the paginated list with the stock when nothing is searched', () => {
    expect(text()).toContain('BRK-001');
    expect(text()).toContain('Giacenza');
    expect(text()).not.toContain('Pertinenza');
  });

  it('shows scored results and the kind of search while searching, instead of the list', async () => {
    await search('filtro olio Fiat Panda');
    const request = backend.expectOne((r) => r.url === '/api/items/search');
    expect(request.request.params.get('limit')).toBe('20');
    request.flush({
      mode: 'HYBRID',
      fallbackReason: null,
      results: [
        {
          itemId: 7,
          code: 'CTL-1',
          description: 'Cartuccia lubrificante motore 1.2 FIRE',
          unit: 'PZ',
          reorderThreshold: 2,
          quantity: 3,
          score: 0.87,
          vectorScore: 0.81,
          textScore: null,
        },
      ],
    });
    await fixture.whenStable();

    expect(text()).toContain('ricerca intelligente');
    expect(text()).toContain('Cartuccia lubrificante motore 1.2 FIRE');
    expect(text()).toContain('87');
    expect(text()).toContain('Pertinenza');
    expect(text()).not.toContain('BRK-001'); // the plain list is hidden while searching
  });

  it('says it is a text search when the smart part was not available', async () => {
    await search('pastiglie');
    backend
      .expectOne((r) => r.url === '/api/items/search')
      .flush({ mode: 'TEXT', fallbackReason: 'NOT_INDEXED', results: [] });
    await fixture.whenStable();

    expect(text()).toContain('ricerca testuale');
    expect(text()).toContain('non è stato indicizzato');
    expect(text()).toContain('Nessun articolo corrisponde alla ricerca.');
  });

  it('returns to the list when the search box is emptied', async () => {
    await search('pastiglie');
    backend.expectOne((r) => r.url === '/api/items/search').flush({ mode: 'HYBRID', fallbackReason: null, results: [] });
    await fixture.whenStable();

    await search('');

    expect(text()).toContain('BRK-001');
    expect(text()).not.toContain('Pertinenza');
  });
});
