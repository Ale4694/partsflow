import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injectable,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';

interface ConfirmRequest {
  message: string;
  confirmLabel: string;
  resolve: (confirmed: boolean) => void;
}

/** Asks the user before a destructive action: `if (await confirm.ask('Eliminare?')) { ... }`. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmRequest | null>(null);

  ask(message: string, confirmLabel = 'Elimina'): Promise<boolean> {
    return new Promise((resolve) => this.request.set({ message, confirmLabel, resolve }));
  }

  answer(confirmed: boolean): void {
    this.request()?.resolve(confirmed);
    this.request.set(null);
  }
}

/** Placed once in the layout; uses the browser's native <dialog>. */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog (cancel)="service.answer(false)">
      @if (service.request(); as request) {
        <p>{{ request.message }}</p>
        <div class="dialog-actions">
          <button type="button" class="btn" (click)="service.answer(false)">Annulla</button>
          <button type="button" class="btn btn-danger" (click)="service.answer(true)">
            {{ request.confirmLabel }}
          </button>
        </div>
      }
    </dialog>
  `,
  styles: `
    dialog {
      border: 0;
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: var(--space-5);
      max-width: 26rem;
      color: var(--color-text);
    }
    dialog::backdrop {
      background: rgb(15 23 42 / 0.45);
    }
    .dialog-actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
      margin-top: var(--space-4);
    }
  `,
})
export class ConfirmDialog {
  protected readonly service = inject(ConfirmService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.service.request() && !element.open) {
        element.showModal();
      } else if (!this.service.request() && element.open) {
        element.close();
      }
    });
  }
}
