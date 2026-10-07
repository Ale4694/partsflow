import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Item, ItemRequest, Page } from '../core/models';

@Injectable({ providedIn: 'root' })
export class ItemsApi {
  private readonly http = inject(HttpClient);

  /** Items whose code or description contains the text (all items when it is empty). Used by the item picker. */
  search(text: () => string) {
    return httpResource<Page<Item>>(() => ({
      url: '/api/items',
      params: { q: text(), size: 8, sort: 'code' },
    }));
  }

  create(request: ItemRequest) {
    return this.http.post<Item>('/api/items', request);
  }

  update(id: number, request: ItemRequest) {
    return this.http.put<Item>(`/api/items/${id}`, request);
  }

  delete(id: number) {
    return this.http.delete<void>(`/api/items/${id}`);
  }
}
