import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AiApi } from '../ai/ai-api';
import { AiStatusService } from '../core/ai-status';
import { ItDatePipe, ItDateTimePipe, MoneyPipe } from '../core/format';
import { Draft, DraftStatus } from '../core/models';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { ImportsApi } from './imports-api';
import { DraftStatusBadge } from './status-badges';

type UploadKind = 'xml' | 'pdf';

/** All drafts, and the two ways to create one: upload a FatturaPA XML, or (with AI) a supplier PDF. */
@Component({
  selector: 'app-import-list',
  imports: [RouterLink, Pager, DraftStatusBadge, MoneyPipe, ItDatePipe, ItDateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './import-list.html',
})
export class ImportList {
  private readonly api = inject(ImportsApi);
  private readonly aiApi = inject(AiApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  protected readonly ai = inject(AiStatusService);

  protected readonly status = signal<DraftStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly drafts = this.api.list(this.status, this.page);
  protected readonly uploading = signal(false);

  protected readonly filters: { value: DraftStatus | null; label: string }[] = [
    { value: null, label: 'Tutte' },
    { value: 'DRAFT', label: 'Bozze' },
    { value: 'CONFIRMED', label: 'Confermate' },
  ];

  protected setStatus(value: string): void {
    this.status.set(value === 'DRAFT' || value === 'CONFIRMED' ? value : null);
    this.page.set(0);
  }

  protected upload(event: Event, kind: UploadKind): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    // Clear the input so the same file can be chosen again after a failure
    input.value = '';
    if (!file) {
      return;
    }
    this.uploading.set(true);
    const call = kind === 'xml' ? this.api.uploadXml(file) : this.aiApi.uploadPdf(file);
    call.subscribe({
      next: (draft: Draft) => {
        this.uploading.set(false);
        this.notifications.success('Bozza creata: controllala prima di confermare');
        this.router.navigate(['/importazioni', draft.id]);
      },
      error: () => this.uploading.set(false),
    });
  }
}
