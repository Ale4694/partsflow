import { ChangeDetectionStrategy, Component, Injectable, inject, signal } from '@angular/core';

export interface Notification {
  id: number;
  kind: 'error' | 'success';
  message: string;
}

/** Shows short messages ("toasts") in the corner of the screen. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly notifications = signal<Notification[]>([]);

  error(message: string): void {
    this.show('error', message, 8000);
  }

  success(message: string): void {
    this.show('success', message, 4000);
  }

  dismiss(id: number): void {
    this.notifications.update((list) => list.filter((n) => n.id !== id));
  }

  private show(kind: Notification['kind'], message: string, milliseconds: number): void {
    const id = this.nextId++;
    this.notifications.update((list) => [...list, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), milliseconds);
  }
}

@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" aria-live="polite">
      @for (n of service.notifications(); track n.id) {
        <div class="toast" [class.toast-error]="n.kind === 'error'" [class.toast-success]="n.kind === 'success'">
          <span>{{ n.message }}</span>
          <button type="button" class="toast-close" aria-label="Chiudi" (click)="service.dismiss(n.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts {
      position: fixed;
      right: var(--space-4);
      bottom: var(--space-4);
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      z-index: 50;
      max-width: min(26rem, calc(100vw - 2 * var(--space-4)));
    }
    .toast {
      display: flex;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      background: var(--color-surface);
      border-left: 4px solid var(--color-muted);
    }
    .toast-error {
      border-left-color: var(--color-danger);
    }
    .toast-success {
      border-left-color: var(--color-success);
    }
    .toast-close {
      border: 0;
      background: none;
      font-size: 1.25rem;
      line-height: 1;
      cursor: pointer;
      color: var(--color-muted);
    }
  `,
})
export class Notifications {
  protected readonly service = inject(NotificationService);
}
