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

  it('searches items by text and searches again when the text changes', () => {
    const text = signal('brake');
    TestBed.runInInjectionContext(() => api.search(text));
    TestBed.tick();

    const first = backend.expectOne((r) => r.url === '/api/items');
    expect(first.request.params.get('q')).toBe('brake');
    expect(first.request.params.get('size')).toBe('8');
    first.flush({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });

    text.set('oil');
    TestBed.tick();
    const second = backend.expectOne((r) => r.url === '/api/items');
    expect(second.request.params.get('q')).toBe('oil');
    second.flush({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
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
