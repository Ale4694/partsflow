import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DraftStatus, LineStatus } from '../core/models';

/** Colour-coded status of one draft line: green = will change stock, amber = needs a decision, grey = ignored. */
@Component({
  selector: 'app-line-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span
    class="badge"
    [class.badge-success]="status() === 'MATCHED'"
    [class.badge-warning]="status() === 'PENDING_REVIEW'"
    >{{ label() }}</span
  >`,
})
export class LineStatusBadge {
  readonly status = input.required<LineStatus>();

  protected readonly label = computed(() => {
    switch (this.status()) {
      case 'MATCHED':
        return 'Abbinata';
      case 'PENDING_REVIEW':
        return 'Da verificare';
      case 'SKIPPED':
        return 'Ignorata';
    }
  });
}

@Component({
  selector: 'app-draft-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class.badge-success]="status() === 'CONFIRMED'" [class.badge-warning]="status() === 'DRAFT'">{{
    status() === 'DRAFT' ? 'Bozza' : 'Confermata'
  }}</span>`,
})
export class DraftStatusBadge {
  readonly status = input.required<DraftStatus>();
}
