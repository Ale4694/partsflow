import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Previous / next buttons for a paginated list. `page` is zero-based, like the API. */
@Component({
  selector: 'app-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="pager" aria-label="Pagine">
        <button type="button" class="btn btn-small" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
          ‹ Precedente
        </button>
        <span class="muted">Pagina {{ humanPage() }} di {{ totalPages() }} ({{ totalElements() }} elementi)</span>
        <button
          type="button"
          class="btn btn-small"
          [disabled]="humanPage() >= totalPages()"
          (click)="pageChange.emit(page() + 1)"
        >
          Successiva ›
        </button>
      </nav>
    }
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-3);
      padding: var(--space-3);
    }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly humanPage = computed(() => this.page() + 1);
}
