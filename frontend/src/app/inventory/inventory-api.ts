import { HttpClient, HttpContext, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PAGE_SIZE } from '../core/api';
import { Page, StockLevel, StockMovement, StockMovementRequest } from '../core/models';
import { SKIP_ERROR_NOTIFICATION } from '../core/problem-detail.interceptor';

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);

  /** Every item with its current stock, ordered by item code. */
  stockLevels(page: () => number) {
    return httpResource<Page<StockLevel>>(() => ({
      url: '/api/inventory/stock',
      params: { page: page(), size: PAGE_SIZE },
    }));
  }

  /** Items below their reorder threshold. */
  lowStock(size = 10) {
    return httpResource<Page<StockLevel>>(() => ({ url: '/api/inventory/low-stock', params: { size } }));
  }

  /** Movement history, newest first; for one item when `itemId` is not null. */
  movements(itemId: () => number | null, page: () => number, size = PAGE_SIZE) {
    return httpResource<Page<StockMovement>>(() => {
      const params: Record<string, number> = { page: page(), size };
      const id = itemId();
      if (id !== null) {
        params['itemId'] = id;
      }
      return { url: '/api/inventory/movements', params };
    });
  }

  /** The movement form shows a failure (for example "insufficient stock") inside the form, so no toast here. */
  record(request: StockMovementRequest) {
    return this.http.post<StockMovement>('/api/inventory/movements', request, {
      context: new HttpContext().set(SKIP_ERROR_NOTIFICATION, true),
    });
  }
}
