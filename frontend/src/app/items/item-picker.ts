import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { debounced } from '../core/debounce';
import { ScorePipe } from '../core/format';
import { PickedItem } from '../core/models';
import { ItemsApi } from './items-api';
import { SearchModeIndicator } from './search-mode';

/** A row of the picker: an item, and how well it matches when it comes from a search. */
interface Entry extends PickedItem {
  score?: number;
}

/**
 * A searchable list of items. Type to search by meaning and by words (the smart search of ADR 0012), click one to
 * pick it. With nothing typed it shows the first items. The parent decides what picking means (fill a form field,
 * resolve an import line...).
 */
@Component({
  selector: 'app-item-picker',
  imports: [ScorePipe, SearchModeIndicator],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="picker">
      <input
        #box
        type="search"
        placeholder="Cerca per codice, descrizione o significato…"
        [attr.aria-label]="label()"
        (input)="onInput(box.value)"
      />
      @if (searching()) {
        @if (search.isLoading() && !search.hasValue()) {
          <p class="muted">Ricerca…</p>
        } @else if (search.hasValue()) {
          <p class="mode">
            <app-search-mode [mode]="search.value().mode" [fallbackReason]="search.value().fallbackReason" />
          </p>
        }
      }
      @if (entries().length === 0 && !loading()) {
        <p class="muted">Nessun articolo trovato.</p>
      }
      <ul>
        @for (entry of entries(); track entry.id) {
          <li>
            <button type="button" [class.selected]="entry.id === selectedId()" (click)="picked.emit(entry)">
              <strong>{{ entry.code }}</strong> — {{ entry.description }}
              @if (entry.score !== undefined) {
                <span class="muted score">{{ entry.score | score }}</span>
              }
            </button>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .picker ul {
      list-style: none;
      margin: var(--space-2) 0 0;
      padding: 0;
      border: 1px solid var(--color-border);
      border-radius: var(--radius);
      max-height: 12rem;
      overflow-y: auto;
      background: var(--color-surface);
    }
    .picker ul:empty {
      display: none;
    }
    li + li {
      border-top: 1px solid var(--color-border);
    }
    li button {
      width: 100%;
      text-align: left;
      padding: var(--space-2) var(--space-3);
      border: 0;
      background: none;
      font: inherit;
      color: inherit;
      cursor: pointer;
    }
    li button:hover,
    li button.selected {
      background: var(--color-bg);
    }
    li button.selected {
      font-weight: 600;
    }
    .mode {
      margin: var(--space-2) 0 0;
    }
    .score {
      float: right;
      font-size: 0.8125rem;
    }
  `,
})
export class ItemPicker {
  /** Accessible name of the search box. */
  readonly label = input('Cerca articolo');
  readonly selectedId = input<number | null>(null);
  readonly picked = output<PickedItem>();

  private readonly api = inject(ItemsApi);
  private readonly query = signal('');
  protected readonly browse = this.api.browse();
  protected readonly search = this.api.search(this.query);

  protected readonly searching = computed(() => this.query().trim() !== '');
  protected readonly loading = computed(() => (this.searching() ? this.search.isLoading() : this.browse.isLoading()));

  /** What the list shows: search hits (best first, with their score) or, with nothing typed, the first items. */
  protected readonly entries = computed<Entry[]>(() => {
    if (this.searching()) {
      return this.search.hasValue()
        ? this.search.value().results.map((hit) => ({
            id: hit.itemId,
            code: hit.code,
            description: hit.description,
            unit: hit.unit,
            score: hit.score,
          }))
        : [];
    }
    return this.browse.hasValue() ? this.browse.value().content : [];
  });

  /** Waits until the user stops typing: one search (and one embedding request) per pause, not per key. */
  protected readonly onInput = debounced((text: string) => this.query.set(text));
}
