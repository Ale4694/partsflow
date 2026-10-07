import { HttpClient, HttpContext, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification';
import {
  SKIP_ERROR_NOTIFICATION,
  UNREACHABLE_MESSAGE,
  isServerUnreachable,
  problemDetailInterceptor,
} from './problem-detail.interceptor';

describe('problemDetailInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => backend.verify());

  it('shows the "detail" of a ProblemDetail and still fails the call', () => {
    let failed = false;
    http.post('/api/inventory/movements', {}).subscribe({ error: () => (failed = true) });

    backend
      .expectOne('/api/inventory/movements')
      .flush(
        { title: 'Conflict', status: 409, detail: 'Insufficient stock for item 3: available 1, requested 5' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(failed).toBe(true);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        kind: 'error',
        message: 'Insufficient stock for item 3: available 1, requested 5',
      }),
    ]);
  });

  it('falls back to the title when there is no detail', () => {
    http.get('/api/items').subscribe({ error: () => undefined });

    backend.expectOne('/api/items').flush({ title: 'Not Found', status: 404 }, { status: 404, statusText: 'Not Found' });

    expect(notifications.notifications()[0].message).toBe('Not Found');
  });

  it('explains that the server is unreachable on a network error', () => {
    http.get('/api/items').subscribe({ error: () => undefined });

    backend.expectOne('/api/items').error(new ProgressEvent('error'));

    expect(notifications.notifications()[0].message).toBe(UNREACHABLE_MESSAGE);
    expect(UNREACHABLE_MESSAGE).toBe('Il server non è raggiungibile, riprova tra qualche secondo.');
  });

  it.each([502, 503, 504])('says the server is unreachable for a %i answered by the proxy', (status) => {
    http.get('/api/items').subscribe({ error: () => undefined });

    // nginx answers with an HTML page, not with a ProblemDetail
    backend.expectOne('/api/items').flush('<html>Bad Gateway</html>', { status, statusText: 'Bad Gateway' });

    expect(notifications.notifications()[0].message).toBe(UNREACHABLE_MESSAGE);
    expect(notifications.notifications()[0].message).not.toContain('Bad Gateway');
  });

  it('copes with an error body that is not a ProblemDetail', () => {
    http.get('/api/items').subscribe({ error: () => undefined });

    backend.expectOne('/api/items').flush('<html>oops</html>', { status: 500, statusText: 'Internal Server Error' });

    expect(notifications.notifications()[0].message).toBe('Errore 500');
  });

  describe('AI errors (the backend sends a "code")', () => {
    it.each([
      ['AI_KEY_MISSING', 503, 'Le funzioni AI non sono attive: sul server manca la chiave LLM_API_KEY.'],
      [
        'AI_REJECTED',
        503,
        'Il servizio AI ha rifiutato la richiesta. Controlla la chiave e il modello configurati sul server.',
      ],
      [
        'AI_TEMPORARILY_UNAVAILABLE',
        503,
        'Il servizio AI è sovraccarico o ha esaurito la quota gratuita. Riprova tra un minuto.',
      ],
      ['AI_BAD_ANSWER', 502, 'Il servizio AI ha risposto in modo non utilizzabile. Riprova.'],
    ])('translates %s', (code, status, expected) => {
      http.post('/api/ai/assistant', {}).subscribe({ error: () => undefined });

      backend
        .expectOne('/api/ai/assistant')
        .flush(
          { status, code, detail: 'English text of the backend' },
          { status, statusText: 'Whatever' },
        );

      expect(notifications.notifications()[0].message).toBe(expected);
    });

    it('does not mistake a 503 of the backend for an unreachable server', () => {
      http.post('/api/ai/assistant', {}).subscribe({ error: () => undefined });

      backend
        .expectOne('/api/ai/assistant')
        .flush({ status: 503, detail: 'Something specific' }, { status: 503, statusText: 'Service Unavailable' });

      expect(notifications.notifications()[0].message).toBe('Something specific');
    });
  });

  describe('isServerUnreachable', () => {
    it('is false for errors that are not HTTP errors', () => {
      expect(isServerUnreachable(new Error('x'))).toBe(false);
    });

    it('is false for a ProblemDetail, even with status 503', () => {
      const error = new HttpErrorResponse({ status: 503, error: { status: 503, detail: 'AI disabled' } });

      expect(isServerUnreachable(error)).toBe(false);
    });

    it('is true for status 0 and for proxy errors without ProblemDetail', () => {
      expect(isServerUnreachable(new HttpErrorResponse({ status: 0 }))).toBe(true);
      expect(isServerUnreachable(new HttpErrorResponse({ status: 502, error: '<html>' }))).toBe(true);
      expect(isServerUnreachable(new HttpErrorResponse({ status: 404, error: '<html>' }))).toBe(false);
    });
  });

  it('shows nothing when the caller handles the error itself', () => {
    const context = new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
    http.get('/api/items', { context }).subscribe({ error: () => undefined });

    backend.expectOne('/api/items').flush({ detail: 'ignored' }, { status: 400, statusText: 'Bad Request' });

    expect(notifications.notifications()).toEqual([]);
  });

  it('shows nothing for successful calls', () => {
    http.get('/api/items').subscribe();

    backend.expectOne('/api/items').flush({});

    expect(notifications.notifications()).toEqual([]);
  });
});
