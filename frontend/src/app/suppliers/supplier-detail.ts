import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmService } from '../core/confirm';
import { Item, SupplierItemCode } from '../core/models';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { ItemPicker } from '../items/item-picker';
import { SuppliersApi } from './suppliers-api';

/** One supplier and the codes it prints on invoices, each mapped to one of our items. */
@Component({
  selector: 'app-supplier-detail',
  imports: [RouterLink, ReactiveFormsModule, ItemPicker, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-detail.html',
})
export class SupplierDetail {
  /** From the route /fornitori/:id (withComponentInputBinding), always text. */
  readonly id = input.required<string>();

  private readonly api = inject(SuppliersApi);
  private readonly confirmation = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);

  private readonly supplierId = computed(() => Number(this.id()));
  protected readonly page = signal(0);
  protected readonly supplier = this.api.get(this.supplierId);
  protected readonly codes = this.api.itemCodes(this.supplierId, this.page);

  /** The mapping being edited; null when adding a new one. */
  protected readonly editing = signal<SupplierItemCode | null>(null);
  protected readonly pickedItem = signal<Item | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    supplierCode: ['', [Validators.required, Validators.maxLength(100)]],
  });

  protected pick(item: Item): void {
    this.pickedItem.set(item);
  }

  protected startEdit(code: SupplierItemCode): void {
    this.editing.set(code);
    this.form.reset({ supplierCode: code.supplierCode });
    this.pickedItem.set(null);
  }

  protected resetForm(): void {
    this.editing.set(null);
    this.form.reset({ supplierCode: '' });
    this.pickedItem.set(null);
  }

  protected save(): void {
    const editing = this.editing();
    const item = this.pickedItem();
    // When editing, keeping the same item is allowed
    const itemId = item?.id ?? editing?.itemId;
    if (this.form.invalid || itemId === undefined) {
      this.form.markAllAsTouched();
      return;
    }
    const request = { supplierCode: this.form.controls.supplierCode.value, itemId };
    const supplierId = this.supplierId();
    const call = editing
      ? this.api.updateItemCode(supplierId, editing.id, request)
      : this.api.createItemCode(supplierId, request);
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(editing ? 'Codice aggiornato' : 'Codice aggiunto');
        this.resetForm();
        this.codes.reload();
      },
      error: () => this.saving.set(false),
    });
  }

  protected async remove(code: SupplierItemCode): Promise<void> {
    const confirmed = await this.confirmation.ask(`Eliminare il codice ${code.supplierCode}?`);
    if (!confirmed) {
      return;
    }
    this.api.deleteItemCode(this.supplierId(), code.id).subscribe(() => {
      this.notifications.success('Codice eliminato');
      this.codes.reload();
    });
  }
}
