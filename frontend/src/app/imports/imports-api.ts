import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PAGE_SIZE } from '../core/api';
import { Draft, DraftStatus, DraftSummary, Page } from '../core/models';

@Injectable({ providedIn: 'root' })
export class ImportsApi {
  private readonly http = inject(HttpClient);

  /** Drafts, newest first; only those with the given status when it is not null. */
  list(status: () => DraftStatus | null, page: () => number) {
    return httpResource<Page<DraftSummary>>(() => {
      const params: Record<string, string | number> = { page: page(), size: PAGE_SIZE };
      const current = status();
      if (current !== null) {
        params['status'] = current;
      }
      return { url: '/api/imports', params };
    });
  }

  get(id: () => number) {
    return httpResource<Draft>(() => `/api/imports/${id()}`);
  }

  /** Uploads a FatturaPA XML file. The result is a draft: the stock does not change yet. */
  uploadXml(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Draft>('/api/imports/fatturapa', body);
  }

  resolveLine(draftId: number, lineId: number, itemId: number) {
    return this.http.post<Draft>(`/api/imports/${draftId}/lines/${lineId}/resolve`, { itemId });
  }

  skipLine(draftId: number, lineId: number) {
    return this.http.post<Draft>(`/api/imports/${draftId}/lines/${lineId}/skip`, {});
  }

  /** The only call that writes stock movements. */
  confirm(draftId: number) {
    return this.http.post<Draft>(`/api/imports/${draftId}/confirm`, {});
  }

  discard(draftId: number) {
    return this.http.delete<void>(`/api/imports/${draftId}`);
  }
}
