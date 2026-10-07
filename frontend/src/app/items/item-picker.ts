import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { Item } from '../core/models';
import { ItemsApi } from './items-api';

/**
 * A searchable list of items. Type to filter by code or description, click one to pick it.
 * The parent decides what picking means (fill a form field, resolve an import line...).
 */
@Component({
  selector: 'app-item-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="picker">
      <input
        #box
        type="search"
        placeholder="Cerca per codice o descrizione…"
        [attr.aria-label]="label()"
        (input)="onInput(box.value)"
      />
      @if (results.isLoading() && !results.hasValue()) {
        <p class="muted">Ricerca…</p>
      } @else if (results.hasValue()) {
        @if (results.value().content.length === 0) {
          <p class="muted">Nessun articolo trovato.</p>
        }
        <ul>
          @for (item of results.value().content; track item.id) {
            <li>
              <button type="button" [class.selected]="item.id === selectedId()" (click)="picked.emit(item)">
                <strong>{{ item.code }}</strong> — {{ item.description }}
              </button>
            </li>
          }
        </ul>
        @if (results.value().totalElements > results.value().content.length) {
          <p class="muted hint">Ci sono altri risultati: restringi la ricerca.</p>
        }
      }
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
    .hint {
      font-size: 0.8125rem;
      margin: var(--space-1) 0 0;
    }
  `,
})
export class ItemPicker {
  /** Accessible name of the search box. */
  readonly label = input('Cerca articolo');
  readonly selectedId = input<number | null>(null);
  readonly picked = output<Item>();

  private readonly query = signal('');
  protected readonly results = inject(ItemsApi).search(this.query);

  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  /** Waits until the user stops typing, so we do not send one request per key. */
  protected onInput(text: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.query.set(text.trim()), 250);
  }
}
