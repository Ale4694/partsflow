import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MovementType } from './models';

@Component({
  selector: 'app-movement-type',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class.badge-success]="type() === 'IN'" [class.badge-warning]="type() === 'OUT'">{{
    type() === 'IN' ? 'Carico' : 'Scarico'
  }}</span>`,
})
export class MovementTypeBadge {
  readonly type = input.required<MovementType>();
}
