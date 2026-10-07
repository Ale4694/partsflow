import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { InventoryApi } from './inventory-api';

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('InventoryApi', () => {
  let api: InventoryApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(InventoryApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('asks for the movements of all items, or of one item when a filter is set', () => {
    const itemId = signal<number | null>(null);
    TestBed.runInInjectionContext(() => api.movements(itemId, () => 0));
    TestBed.tick();

    const all = backend.expectOne((r) => r.url === '/api/inventory/movements');
    expect(all.request.params.has('itemId')).toBe(false);
    all.flush(emptyPage);

    itemId.set(7);
    TestBed.tick();
    const filtered = backend.expectOne((r) => r.url === '/api/inventory/movements');
    expect(filtered.request.params.get('itemId')).toBe('7');
    filtered.flush(emptyPage);
  });

  it('reads the stock overview and the low stock list', () => {
    TestBed.runInInjectionContext(() => {
      api.stockLevels(() => 1);
      api.lowStock(5);
    });
    TestBed.tick();

    const stock = backend.expectOne((r) => r.url === '/api/inventory/stock');
    expect(stock.request.params.get('page')).toBe('1');
    stock.flush(emptyPage);

    const low = backend.expectOne((r) => r.url === '/api/inventory/low-stock');
    expect(low.request.params.get('size')).toBe('5');
    low.flush(emptyPage);
  });

  it('records a movement', () => {
    const body = { itemId: 1, type: 'OUT' as const, quantity: 2.5, reason: 'vendita', sourceDocument: null };
    api.record(body).subscribe();

    const request = backend.expectOne('/api/inventory/movements');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });
});
