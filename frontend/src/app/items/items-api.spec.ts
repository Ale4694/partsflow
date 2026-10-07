import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ItemsApi } from './items-api';

describe('ItemsApi', () => {
  let api: ItemsApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ItemsApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const emptyResult = { mode: 'HYBRID', fallbackReason: null, results: [] };

  it('sends no search request while the text is empty and searches again when it changes', () => {
    const text = signal('');
    TestBed.runInInjectionContext(() => api.search(text));
    TestBed.tick();
    backend.expectNone((r) => r.url === '/api/items/search');

    text.set('  filtro olio Fiat Panda ');
    TestBed.tick();
    const first = backend.expectOne((r) => r.url === '/api/items/search');
    expect(first.request.params.get('q')).toBe('filtro olio Fiat Panda'); // trimmed
    expect(first.request.params.get('limit')).toBe('8');
    first.flush(emptyResult);

    text.set('oil');
    TestBed.tick();
    const second = backend.expectOne((r) => r.url === '/api/items/search');
    expect(second.request.params.get('q')).toBe('oil');
    second.flush(emptyResult);
  });

  it('asks for a bigger result list when the caller wants one', () => {
    TestBed.runInInjectionContext(() => api.search(() => 'x', 20));
    TestBed.tick();

    const request = backend.expectOne((r) => r.url === '/api/items/search');
    expect(request.request.params.get('limit')).toBe('20');
    request.flush(emptyResult);
  });

  it('browses the first items by code', () => {
    TestBed.runInInjectionContext(() => api.browse());
    TestBed.tick();

    const request = backend.expectOne((r) => r.url === '/api/items');
    expect(request.request.params.get('size')).toBe('8');
    expect(request.request.params.get('sort')).toBe('code');
    expect(request.request.params.has('q')).toBe(false);
    request.flush({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
  });

  it('creates, updates and deletes items', () => {
    const body = { code: 'BRK-001', description: 'Front brake pad set', unit: 'PZ', reorderThreshold: 5 };

    api.create(body).subscribe();
    const post = backend.expectOne('/api/items');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual(body);
    post.flush({ id: 1, ...body });

    api.update(1, body).subscribe();
    const put = backend.expectOne('/api/items/1');
    expect(put.request.method).toBe('PUT');
    put.flush({ id: 1, ...body });

    api.delete(1).subscribe();
    const del = backend.expectOne('/api/items/1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
