import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SuppliersApi } from './suppliers-api';

describe('SuppliersApi', () => {
  let api: SuppliersApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SuppliersApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists suppliers one page at a time', async () => {
    const page = { content: [{ id: 1, name: 'Ricambi Rossi Srl', vatNumber: '20000000001' }], page: 2, size: 20, totalElements: 41, totalPages: 3 };
    const suppliers = TestBed.runInInjectionContext(() => api.list(() => 2));
    TestBed.tick();

    const request = backend.expectOne((r) => r.url === '/api/suppliers');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
    request.flush(page);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(suppliers.value()).toEqual(page);
  });

  it('creates a supplier', () => {
    const body = { name: 'Ricambi Rossi Srl', vatNumber: '20000000001' };
    let created: unknown;
    api.create(body).subscribe((supplier) => (created = supplier));

    const request = backend.expectOne('/api/suppliers');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ id: 1, ...body });

    expect(created).toEqual({ id: 1, ...body });
  });

  it('updates and deletes a supplier', () => {
    api.update(3, { name: 'Nuovo nome', vatNumber: '20000000001' }).subscribe();
    const put = backend.expectOne('/api/suppliers/3');
    expect(put.request.method).toBe('PUT');
    put.flush({});

    api.delete(3).subscribe();
    const del = backend.expectOne('/api/suppliers/3');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('manages the item codes of one supplier', () => {
    api.createItemCode(1, { supplierCode: 'RR-BRK-001', itemId: 5 }).subscribe();
    const post = backend.expectOne('/api/suppliers/1/item-codes');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ supplierCode: 'RR-BRK-001', itemId: 5 });
    post.flush({});

    api.updateItemCode(1, 9, { supplierCode: 'RR-BRK-001', itemId: 6 }).subscribe();
    const put = backend.expectOne('/api/suppliers/1/item-codes/9');
    expect(put.request.method).toBe('PUT');
    put.flush({});

    api.deleteItemCode(1, 9).subscribe();
    const del = backend.expectOne('/api/suppliers/1/item-codes/9');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
