import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AiApi } from './ai-api';

describe('AiApi', () => {
  let api: AiApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AiApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('uploads a PDF to the AI import endpoint', () => {
    const file = new File(['%PDF'], 'ddt.pdf', { type: 'application/pdf' });
    api.uploadPdf(file).subscribe();

    const request = backend.expectOne('/api/ai/imports/pdf');
    expect(request.request.method).toBe('POST');
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({});
  });

  it('does not request suggestions while the AI is unavailable', () => {
    const enabled = signal(false);
    TestBed.runInInjectionContext(() => api.suggestions(() => 4, enabled));
    TestBed.tick();
    backend.expectNone('/api/ai/imports/4/suggestions');

    enabled.set(true);
    TestBed.tick();
    backend.expectOne('/api/ai/imports/4/suggestions').flush([]);
  });

  it('asks for matches, accepts and rejects suggestions', () => {
    api.suggestMatches(4).subscribe();
    backend.expectOne('/api/ai/imports/4/suggest-matches').flush({ suggestions: [], linesLeftForNextRequest: 0 });

    api.accept(8).subscribe();
    const accept = backend.expectOne('/api/ai/suggestions/8/accept');
    expect(accept.request.method).toBe('POST');
    accept.flush({});

    api.reject(8).subscribe();
    backend.expectOne('/api/ai/suggestions/8/reject').flush({});
  });

  it('sends a question to the assistant', () => {
    let answer = '';
    api.ask('Quanti BRK-001?').subscribe((reply) => (answer = reply.answer));

    const request = backend.expectOne('/api/ai/assistant');
    expect(request.request.body).toEqual({ question: 'Quanti BRK-001?' });
    request.flush({ answer: '10 pezzi', toolCalls: 1 });

    expect(answer).toBe('10 pezzi');
  });
});
