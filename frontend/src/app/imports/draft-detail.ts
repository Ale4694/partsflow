import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AiApi } from '../ai/ai-api';
import { AiStatusService } from '../core/ai-status';
import { ConfirmService } from '../core/confirm';
import { ItDatePipe, ItDateTimePipe, MoneyPipe, PercentPipe, QuantityPipe } from '../core/format';
import { DraftLine, Item, Suggestion } from '../core/models';
import { NotificationService } from '../core/notification';
import { ItemPicker } from '../items/item-picker';
import { ImportsApi } from './imports-api';
import { DraftStatusBadge, LineStatusBadge } from './status-badges';

/**
 * One draft: header data, every line with its status, and the decisions a person must take before confirming.
 * "Conferma" is the only button that changes the stock, and it stays disabled while a line is pending review.
 */
@Component({
  selector: 'app-draft-detail',
  imports: [
    RouterLink,
    ItemPicker,
    LineStatusBadge,
    DraftStatusBadge,
    MoneyPipe,
    QuantityPipe,
    PercentPipe,
    ItDatePipe,
    ItDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './draft-detail.html',
  styleUrl: './draft-detail.css',
})
export class DraftDetail {
  /** From the route /importazioni/:id (withComponentInputBinding), always text. */
  readonly id = input.required<string>();

  private readonly api = inject(ImportsApi);
  private readonly aiApi = inject(AiApi);
  private readonly router = inject(Router);
  private readonly confirmation = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  protected readonly ai = inject(AiStatusService);

  private readonly draftId = computed(() => Number(this.id()));
  protected readonly draft = this.api.get(this.draftId);

  /** Line whose item picker is open. */
  protected readonly resolvingLineId = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly suggesting = signal(false);

  /** Stays disabled while any line still needs a decision, and once the draft is confirmed. */
  protected readonly canConfirm = computed(
    () => this.draft.hasValue() && this.draft.value().status === 'DRAFT' && this.draft.value().pendingLines === 0,
  );

  // ---- AI suggestions (only requested when the AI is available and the draft is still open) ----
  private readonly suggestionsEnabled = computed(
    () => this.ai.available() && this.draft.hasValue() && this.draft.value().status === 'DRAFT',
  );
  private readonly suggestions = this.aiApi.suggestions(this.draftId, this.suggestionsEnabled);
  private readonly openSuggestions = computed(() =>
    new Map<number, Suggestion>(
      (this.suggestions.hasValue() ? this.suggestions.value() : [])
        .filter((suggestion) => suggestion.status === 'SUGGESTED')
        .map((suggestion) => [suggestion.draftLineId, suggestion]),
    ),
  );

  protected suggestionFor(line: DraftLine): Suggestion | undefined {
    return line.status === 'PENDING_REVIEW' ? this.openSuggestions().get(line.id) : undefined;
  }

  // ---- decisions on a pending line ----

  protected toggleResolving(line: DraftLine): void {
    this.resolvingLineId.update((current) => (current === line.id ? null : line.id));
  }

  protected resolve(line: DraftLine, item: Item): void {
    this.run(this.api.resolveLine(this.draftId(), line.id, item.id), 'Riga abbinata');
  }

  protected skip(line: DraftLine): void {
    this.run(this.api.skipLine(this.draftId(), line.id), 'Riga ignorata: non verrà caricata a magazzino');
  }

  private run(call: ReturnType<ImportsApi['skipLine']>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.resolvingLineId.set(null);
        this.draft.set(updated);
        this.notifications.success(message);
      },
      error: () => this.busy.set(false),
    });
  }

  // ---- AI ----

  protected suggestMatches(): void {
    this.suggesting.set(true);
    this.aiApi.suggestMatches(this.draftId()).subscribe({
      next: (run) => {
        this.suggesting.set(false);
        this.suggestions.reload();
        const left = run.linesLeftForNextRequest;
        this.notifications.success(
          left > 0 ? `Suggerimenti creati. Restano ${left} righe: richiedili di nuovo.` : 'Suggerimenti creati',
        );
      },
      error: () => this.suggesting.set(false),
    });
  }

  protected accept(suggestion: Suggestion): void {
    this.busy.set(true);
    this.aiApi.accept(suggestion.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('Suggerimento accettato: riga abbinata');
        this.draft.reload();
        this.suggestions.reload();
      },
      error: () => this.busy.set(false),
    });
  }

  protected reject(suggestion: Suggestion): void {
    this.busy.set(true);
    this.aiApi.reject(suggestion.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.suggestions.reload();
      },
      error: () => this.busy.set(false),
    });
  }

  // ---- confirm / discard ----

  protected async confirm(): Promise<void> {
    const confirmed = await this.confirmation.ask(
      'Confermare la bozza? Il magazzino verrà aggiornato e l’operazione non si può annullare.',
      'Conferma',
    );
    if (confirmed) {
      this.run(this.api.confirm(this.draftId()), 'Bozza confermata: movimenti di magazzino registrati');
    }
  }

  protected async discard(): Promise<void> {
    const confirmed = await this.confirmation.ask('Scartare questa bozza? Potrai caricare di nuovo il file.', 'Scarta');
    if (!confirmed) {
      return;
    }
    this.busy.set(true);
    this.api.discard(this.draftId()).subscribe({
      next: () => {
        this.notifications.success('Bozza scartata');
        this.router.navigate(['/importazioni']);
      },
      error: () => this.busy.set(false),
    });
  }
}
