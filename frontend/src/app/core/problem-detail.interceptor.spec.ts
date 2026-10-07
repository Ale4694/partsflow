import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification';
import { SKIP_ERROR_NOTIFICATION, problemDetailInterceptor } from './problem-detail.interceptor';

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

    expect(notifications.notifications()[0].message).toContain('Server non raggiungibile');
  });

  it('copes with an error body that is not a ProblemDetail', () => {
    http.get('/api/items').subscribe({ error: () => undefined });

    backend.expectOne('/api/items').flush('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' });

    expect(notifications.notifications()[0].message).toBe('Bad Gateway');
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
