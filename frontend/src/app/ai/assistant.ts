import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AiStatusService } from '../core/ai-status';
import { AiApi } from './ai-api';

interface ChatMessage {
  from: 'user' | 'assistant';
  text: string;
}

/** A simple chat with the inventory assistant. It only reads the inventory; it never changes it. */
@Component({
  selector: 'app-assistant',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assistant.html',
  styleUrl: './assistant.css',
})
export class Assistant {
  private readonly api = inject(AiApi);
  protected readonly ai = inject(AiStatusService);

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly waiting = signal(false);

  protected readonly examples = [
    'Quali articoli sono sotto la soglia di riordino?',
    'Quanti pezzi abbiamo dell’articolo BRK-001?',
    'Mostrami gli ultimi movimenti di OIL-530',
  ];

  protected readonly form = inject(FormBuilder).nonNullable.group({
    question: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected use(example: string): void {
    this.form.controls.question.setValue(example);
  }

  protected send(): void {
    const question = this.form.controls.question.value.trim();
    if (!question || this.waiting() || !this.ai.available()) {
      return;
    }
    this.messages.update((list) => [...list, { from: 'user', text: question }]);
    this.form.reset({ question: '' });
    this.waiting.set(true);
    this.api.ask(question).subscribe({
      next: (reply) => {
        this.waiting.set(false);
        this.messages.update((list) => [...list, { from: 'assistant', text: reply.answer }]);
      },
      error: () => this.waiting.set(false),
    });
  }
}
