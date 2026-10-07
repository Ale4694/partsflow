import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Item, ItemRequest, ItemSearchResult, Page } from '../core/models';

@Injectable({ providedIn: 'root' })
export class ItemsApi {
  private readonly http = inject(HttpClient);

  /** The first items by code: what the item picker shows before anything is typed. */
  browse(size = 8) {
    return httpResource<Page<Item>>(() => ({ url: '/api/items', params: { size, sort: 'code' } }));
  }

  /**
   * Smart search (meaning and spelling, see ADR 0012). Sends no request while the text is empty. The answer says
   * which mode produced it: HYBRID, or TEXT when the smart part was not available.
   */
  search(text: () => string, limit = 8) {
    return httpResource<ItemSearchResult>(() => {
      const query = text().trim();
      return query ? { url: '/api/items/search', params: { q: query, limit } } : undefined;
    });
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
