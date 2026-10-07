import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ConfirmService } from '../core/confirm';
import { QuantityPipe } from '../core/format';
import { Item, StockLevel } from '../core/models';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { InventoryApi } from '../inventory/inventory-api';
import { ItemForm } from './item-form';
import { ItemsApi } from './items-api';

@Component({
  selector: 'app-item-list',
  imports: [ItemForm, Pager, QuantityPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './item-list.html',
})
export class ItemList {
  private readonly itemsApi = inject(ItemsApi);
  private readonly confirmation = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);

  protected readonly page = signal(0);
  // The list comes from the stock overview: every item together with its current stock
  protected readonly levels = inject(InventoryApi).stockLevels(this.page);

  /** null = form closed; an object (even with no id) = form open */
  protected readonly editing = signal<{ item: Item | null } | null>(null);

  protected openCreate(): void {
    this.editing.set({ item: null });
  }

  protected openEdit(level: StockLevel): void {
    this.editing.set({
      item: {
        id: level.itemId,
        code: level.itemCode,
        description: level.description,
        unit: level.unit,
        reorderThreshold: level.reorderThreshold,
      },
    });
  }

  protected onSaved(): void {
    this.editing.set(null);
    this.levels.reload();
  }

  protected async remove(level: StockLevel): Promise<void> {
    const confirmed = await this.confirmation.ask(`Eliminare l’articolo ${level.itemCode}?`);
    if (!confirmed) {
      return;
    }
    this.itemsApi.delete(level.itemId).subscribe(() => {
      this.notifications.success('Articolo eliminato');
      this.levels.reload();
    });
  }
}
