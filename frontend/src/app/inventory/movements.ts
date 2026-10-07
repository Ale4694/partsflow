import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ItDateTimePipe, QuantityPipe } from '../core/format';
import { Item, MovementType } from '../core/models';
import { MovementTypeBadge } from '../core/movement-type-badge';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { problemMessage } from '../core/problem-detail.interceptor';
import { ItemPicker } from '../items/item-picker';
import { InventoryApi } from './inventory-api';

/** Movement history (optionally for one item) and the form to record a new IN or OUT movement. */
@Component({
  selector: 'app-movements',
  imports: [ReactiveFormsModule, ItemPicker, Pager, MovementTypeBadge, QuantityPipe, ItDateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movements.html',
})
export class Movements {
  private readonly api = inject(InventoryApi);
  private readonly notifications = inject(NotificationService);

  // ---- history ----
  protected readonly filterItem = signal<Item | null>(null);
  protected readonly page = signal(0);
  protected readonly movements = this.api.movements(() => this.filterItem()?.id ?? null, this.page);

  // ---- new movement ----
  protected readonly formItem = signal<Item | null>(null);
  protected readonly saving = signal(false);
  /** Shown inside the form, e.g. "Insufficient stock for item 3: available 1, requested 5" (HTTP 409). */
  protected readonly formError = signal<string | null>(null);

  protected readonly types: { value: MovementType; label: string }[] = [
    { value: 'IN', label: 'Carico (entrata)' },
    { value: 'OUT', label: 'Scarico (uscita)' },
  ];

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    type: this.fb.nonNullable.control<MovementType>('IN'),
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    reason: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(200)]),
    sourceDocument: this.fb.nonNullable.control('', [Validators.maxLength(200)]),
  });

  protected setFilter(item: Item | null): void {
    this.filterItem.set(item);
    this.page.set(0);
  }

  protected save(): void {
    const item = this.formItem();
    if (this.form.invalid || !item) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .record({
        itemId: item.id,
        type: value.type,
        quantity: value.quantity ?? 0,
        reason: value.reason,
        sourceDocument: value.sourceDocument.trim() || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifications.success('Movimento registrato');
          this.form.reset({ type: value.type, quantity: null, reason: '', sourceDocument: '' });
          this.movements.reload();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.formError.set(problemMessage(error));
        },
      });
  }
}
