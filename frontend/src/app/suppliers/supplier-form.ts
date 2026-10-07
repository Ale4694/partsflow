import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Supplier } from '../core/models';
import { NotificationService } from '../core/notification';
import { SuppliersApi } from './suppliers-api';

/** Create form, or edit form when a `supplier` is given. */
@Component({
  selector: 'app-supplier-form',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="card" [formGroup]="form" (ngSubmit)="save()">
      <h2>{{ supplier() ? 'Modifica fornitore' : 'Nuovo fornitore' }}</h2>
      <div class="form-grid">
        <div class="field">
          <label for="supplier-name">Ragione sociale</label>
          <input id="supplier-name" formControlName="name" maxlength="200" />
          @if (form.controls.name.touched && form.controls.name.invalid) {
            <span class="field-error">Obbligatoria (max 200 caratteri)</span>
          }
        </div>
        <div class="field">
          <label for="supplier-vat">Partita IVA</label>
          <input id="supplier-vat" formControlName="vatNumber" maxlength="28" placeholder="20000000001" />
          @if (form.controls.vatNumber.touched && form.controls.vatNumber.invalid) {
            <span class="field-error">Da 8 a 28 lettere maiuscole o cifre, senza prefisso del Paese</span>
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
export class SupplierForm {
  readonly supplier = input<Supplier | null>(null);
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  private readonly api = inject(SuppliersApi);
  private readonly notifications = inject(NotificationService);

  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    // Same rule as the backend (SupplierRequest)
    vatNumber: ['', [Validators.required, Validators.pattern('[A-Z0-9]{8,28}')]],
  });

  constructor() {
    effect(() => {
      const supplier = this.supplier();
      this.form.reset(supplier ? { name: supplier.name, vatNumber: supplier.vatNumber } : { name: '', vatNumber: '' });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.form.getRawValue();
    const current = this.supplier();
    const call = current ? this.api.update(current.id, request) : this.api.create(request);
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(current ? 'Fornitore aggiornato' : 'Fornitore creato');
        this.saved.emit();
      },
      error: () => this.saving.set(false),
    });
  }
}
