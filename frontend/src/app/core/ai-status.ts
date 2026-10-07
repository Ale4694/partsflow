import { HttpContext, httpResource } from '@angular/common/http';
import { Injectable, computed } from '@angular/core';
import { AiStatus } from './models';
import { SKIP_ERROR_NOTIFICATION } from './problem-detail.interceptor';

/**
 * - loading: no answer yet
 * - available: the server answered and the AI is configured
 * - key-missing: the server answered and says there is no LLM_API_KEY
 * - unreachable: the server did not answer, so we do NOT know whether the key is set
 */
export type AiState = 'loading' | 'available' | 'key-missing' | 'unreachable';

/** Whether the AI features can be used. Shared by every screen that needs it. */
@Injectable({ providedIn: 'root' })
export class AiStatusService {
  private readonly status = httpResource<AiStatus>(() => ({
    url: '/api/ai/status',
    context: new HttpContext().set(SKIP_ERROR_NOTIFICATION, true),
  }));

  /** "key-missing" only comes from a real answer of /api/ai/status. */
  readonly state = computed<AiState>(() => {
    if (this.status.hasValue()) {
      return this.status.value().available ? 'available' : 'key-missing';
    }
    return this.status.error() ? 'unreachable' : 'loading';
  });

  readonly available = computed(() => this.state() === 'available');
  readonly loading = computed(() => this.state() === 'loading');

  /** Asks the server again (after "server unreachable"). */
  retry(): void {
    this.status.reload();
  }
}
