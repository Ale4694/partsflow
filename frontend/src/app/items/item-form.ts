import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Item } from '../core/models';
import { NotificationService } from '../core/notification';
import { ItemsApi } from './items-api';

/** Create form, or edit form when an `item` is given. */
@Component({
  selector: 'app-item-form',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="card" [formGroup]="form" (ngSubmit)="save()">
      <h2>{{ item() ? 'Modifica articolo' : 'Nuovo articolo' }}</h2>
      <div class="form-grid">
        <div class="field">
          <label for="item-code">Codice interno</label>
          <input id="item-code" formControlName="code" maxlength="50" />
          @if (form.controls.code.touched && form.controls.code.invalid) {
            <span class="field-error">Obbligatorio (max 50 caratteri)</span>
          }
        </div>
        <div class="field">
          <label for="item-description">Descrizione</label>
          <input id="item-description" formControlName="description" maxlength="500" />
          @if (form.controls.description.touched && form.controls.description.invalid) {
            <span class="field-error">Obbligatoria (max 500 caratteri)</span>
          }
        </div>
        <div class="field">
          <label for="item-unit">Unità di misura</label>
          <input id="item-unit" formControlName="unit" maxlength="10" placeholder="PZ" />
          @if (form.controls.unit.touched && form.controls.unit.invalid) {
            <span class="field-error">Obbligatoria (max 10 caratteri)</span>
          }
        </div>
        <div class="field">
          <label for="item-threshold">Soglia di riordino</label>
          <input id="item-threshold" type="number" min="0" step="any" formControlName="reorderThreshold" />
          @if (form.controls.reorderThreshold.touched && form.controls.reorderThreshold.invalid) {
            <span class="field-error">Numero maggiore o uguale a 0</span>
          }
        </div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn" (click)="cancelled.emit()">Annulla</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">Salva</button>
      </div>
    </form>
  `,
})
export class ItemForm {
  readonly item = input<Item | null>(null);
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  private readonly api = inject(ItemsApi);
  private readonly notifications = inject(NotificationService);

  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
    description: ['', [Validators.required, Validators.maxLength(500)]],
    unit: ['', [Validators.required, Validators.maxLength(10)]],
    reorderThreshold: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    // Fill the form when editing, empty it when creating
    effect(() => {
      const item = this.item();
      this.form.reset(item ? { ...item } : { code: '', description: '', unit: '', reorderThreshold: 0 });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.form.getRawValue();
    const current = this.item();
    const call = current ? this.api.update(current.id, request) : this.api.create(request);
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(current ? 'Articolo aggiornato' : 'Articolo creato');
        this.saved.emit();
      },
      error: () => this.saving.set(false),
    });
  }
}
