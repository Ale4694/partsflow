import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SearchFallbackReason, SearchMode } from '../core/models';

/** Tells which kind of search produced the results, and when the smart one was not available, why. */
@Component({
  selector: 'app-search-mode',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (mode() === 'HYBRID') {
      <span class="badge badge-success" title="Cerca per significato e per parole">ricerca intelligente</span>
    } @else {
      <span class="badge" [title]="explanation()">ricerca testuale</span>
      @if (fallbackReason()) {
        <span class="muted note">{{ explanation() }}</span>
      }
    }
  `,
  styles: `
    .note {
      font-size: 0.8125rem;
      margin-left: var(--space-2);
    }
  `,
})
export class SearchModeIndicator {
  readonly mode = input.required<SearchMode>();
  readonly fallbackReason = input<SearchFallbackReason | null>(null);

  protected readonly explanation = computed(() => {
    switch (this.fallbackReason()) {
      case 'NOT_CONFIGURED':
        return 'La ricerca intelligente non è configurata sul server.';
      case 'DIMENSION_MISMATCH':
        return 'La ricerca intelligente è disattivata: la dimensione degli embedding non corrisponde al database.';
      case 'NOT_INDEXED':
        return 'La ricerca intelligente non è ancora pronta: il catalogo non è stato indicizzato.';
      case 'PROVIDER_ERROR':
        return 'La ricerca intelligente non è raggiungibile (servizio AI senza risposta o senza quota).';
      default:
        return 'Cerca solo per parole.';
    }
  });
}
