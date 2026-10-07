import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PAGE_SIZE } from '../core/api';
import { Page, Supplier, SupplierItemCode, SupplierItemCodeRequest, SupplierRequest } from '../core/models';

/** Everything the backend offers about suppliers and their item codes. */
@Injectable({ providedIn: 'root' })
export class SuppliersApi {
  private readonly http = inject(HttpClient);

  // ---- reads: resources that reload by themselves when the page or the id signal changes ----

  list(page: () => number) {
    return httpResource<Page<Supplier>>(() => ({
      url: '/api/suppliers',
      params: { page: page(), size: PAGE_SIZE, sort: 'name' },
    }));
  }

  get(id: () => number) {
    return httpResource<Supplier>(() => `/api/suppliers/${id()}`);
  }

  itemCodes(supplierId: () => number, page: () => number) {
    return httpResource<Page<SupplierItemCode>>(() => ({
      url: `/api/suppliers/${supplierId()}/item-codes`,
      params: { page: page(), size: PAGE_SIZE, sort: 'supplierCode' },
    }));
  }

  // ---- writes ----

  create(request: SupplierRequest) {
    return this.http.post<Supplier>('/api/suppliers', request);
  }

  update(id: number, request: SupplierRequest) {
    return this.http.put<Supplier>(`/api/suppliers/${id}`, request);
  }

  delete(id: number) {
    return this.http.delete<void>(`/api/suppliers/${id}`);
  }

  createItemCode(supplierId: number, request: SupplierItemCodeRequest) {
    return this.http.post<SupplierItemCode>(`/api/suppliers/${supplierId}/item-codes`, request);
  }

  updateItemCode(supplierId: number, id: number, request: SupplierItemCodeRequest) {
    return this.http.put<SupplierItemCode>(`/api/suppliers/${supplierId}/item-codes/${id}`, request);
  }

  deleteItemCode(supplierId: number, id: number) {
    return this.http.delete<void>(`/api/suppliers/${supplierId}/item-codes/${id}`);
  }
}
