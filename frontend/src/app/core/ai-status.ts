import { HttpContext, httpResource } from '@angular/common/http';
import { Injectable, computed } from '@angular/core';
import { SKIP_ERROR_NOTIFICATION } from './problem-detail.interceptor';
import { AiStatus } from './models';

/** Whether the AI features can be used (the backend has an LLM key). Shared by every screen that needs it. */
@Injectable({ providedIn: 'root' })
export class AiStatusService {
  private readonly status = httpResource<AiStatus>(() => ({
    url: '/api/ai/status',
    context: new HttpContext().set(SKIP_ERROR_NOTIFICATION, true),
  }));

  /** True only when the backend answered "available". While loading or on error: false. */
  readonly available = computed(() => this.status.hasValue() && this.status.value().available);
  readonly loading = computed(() => this.status.isLoading());
}
