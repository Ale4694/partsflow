import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AssistantAnswer, Draft, MatchRun, Suggestion } from '../core/models';

/** The AI endpoints. They all answer 503 when the backend has no LLM key (see AiStatusService). */
@Injectable({ providedIn: 'root' })
export class AiApi {
  private readonly http = inject(HttpClient);

  /** Uploads a supplier PDF. Like an XML import, the result is a draft to review. */
  uploadPdf(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Draft>('/api/ai/imports/pdf', body);
  }

  /** Suggestions already stored for a draft. `enabled` false = no request is sent (AI unavailable). */
  suggestions(draftId: () => number, enabled: () => boolean) {
    return httpResource<Suggestion[]>(() => (enabled() ? `/api/ai/imports/${draftId()}/suggestions` : undefined));
  }

  suggestMatches(draftId: number) {
    return this.http.post<MatchRun>(`/api/ai/imports/${draftId}/suggest-matches`, {});
  }

  accept(suggestionId: number) {
    return this.http.post<Suggestion>(`/api/ai/suggestions/${suggestionId}/accept`, {});
  }

  reject(suggestionId: number) {
    return this.http.post<Suggestion>(`/api/ai/suggestions/${suggestionId}/reject`, {});
  }

  ask(question: string) {
    return this.http.post<AssistantAnswer>('/api/ai/assistant', { question });
  }
}
