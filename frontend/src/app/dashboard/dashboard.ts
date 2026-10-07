import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AiStatusService } from '../core/ai-status';
import { ItDateTimePipe, QuantityPipe } from '../core/format';
import { MovementTypeBadge } from '../core/movement-type-badge';
import { ImportsApi } from '../imports/imports-api';
import { InventoryApi } from '../inventory/inventory-api';

/** The first screen: what needs attention right now. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MovementTypeBadge, QuantityPipe, ItDateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly inventory = inject(InventoryApi);
  protected readonly lowStock = this.inventory.lowStock(10);
  protected readonly latestMovements = this.inventory.movements(
    () => null,
    () => 0,
    10,
  );
  protected readonly openDrafts = inject(ImportsApi).list(
    () => 'DRAFT',
    () => 0,
  );
  protected readonly ai = inject(AiStatusService);
}
