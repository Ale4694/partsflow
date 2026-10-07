import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ConfirmService } from '../core/confirm';
import { debounced } from '../core/debounce';
import { QuantityPipe, ScorePipe } from '../core/format';
import { Item, ItemHit, StockLevel } from '../core/models';
import { NotificationService } from '../core/notification';
import { Pager } from '../core/pager';
import { InventoryApi } from '../inventory/inventory-api';
import { ItemForm } from './item-form';
import { ItemsApi } from './items-api';
import { SearchModeIndicator } from './search-mode';

@Component({
  selector: 'app-item-list',
  imports: [ItemForm, Pager, QuantityPipe, ScorePipe, SearchModeIndicator],
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

  // ---- smart search: meaning and words (ADR 0012). With nothing typed the paginated list below is shown. ----
  private readonly query = signal('');
  protected readonly search = this.itemsApi.search(this.query, 20);
  protected readonly searching = computed(() => this.query().trim() !== '');
  /** One search (and one embedding request) per pause in typing, not per key. */
  protected readonly onSearchInput = debounced((text: string) => this.query.set(text));

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

  /** Edit an item found by the search (a hit has everything the form needs). */
  protected openEditHit(hit: ItemHit): void {
    this.editing.set({
      item: {
        id: hit.itemId,
        code: hit.code,
        description: hit.description,
        unit: hit.unit,
        reorderThreshold: hit.reorderThreshold,
      },
    });
  }

  protected onSaved(): void {
    this.editing.set(null);
    this.levels.reload();
    if (this.searching()) {
      this.search.reload();
    }
  }

  protected async remove(level: StockLevel): Promise<void> {
    const confirmed = await this.confirmation.ask(`Eliminare l’articolo ${level.itemCode}?`);
    if (!confirmed) {
      return;
    }
    this.deleteItem(level.itemId);
  }

  protected async removeHit(hit: ItemHit): Promise<void> {
    const confirmed = await this.confirmation.ask(`Eliminare l’articolo ${hit.code}?`);
    if (confirmed) {
      this.deleteItem(hit.itemId);
    }
  }

  private deleteItem(id: number): void {
    this.itemsApi.delete(id).subscribe(() => {
      this.notifications.success('Articolo eliminato');
      this.levels.reload();
      if (this.searching()) {
        this.search.reload();
      }
    });
  }
}
