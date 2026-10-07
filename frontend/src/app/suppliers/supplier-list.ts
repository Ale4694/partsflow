import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmService } from '../core/confirm';
import { Supplier } from '../core/models';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { SupplierForm } from './supplier-form';
import { SuppliersApi } from './suppliers-api';

@Component({
  selector: 'app-supplier-list',
  imports: [RouterLink, SupplierForm, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-list.html',
})
export class SupplierList {
  private readonly api = inject(SuppliersApi);
  private readonly confirmation = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);

  protected readonly page = signal(0);
  protected readonly suppliers = this.api.list(this.page);

  /** null = form closed; an object (even with no supplier) = form open */
  protected readonly editing = signal<{ supplier: Supplier | null } | null>(null);

  protected onSaved(): void {
    this.editing.set(null);
    this.suppliers.reload();
  }

  protected async remove(supplier: Supplier): Promise<void> {
    const confirmed = await this.confirmation.ask(`Eliminare il fornitore ${supplier.name}?`);
    if (!confirmed) {
      return;
    }
    this.api.delete(supplier.id).subscribe(() => {
      this.notifications.success('Fornitore eliminato');
      this.suppliers.reload();
    });
  }
}
