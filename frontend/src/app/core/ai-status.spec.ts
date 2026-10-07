import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AiStatusService } from './ai-status';

describe('AiStatusService', () => {
  let backend: HttpTestingController;
  let service: AiStatusService;

  async function settle(): Promise<void> {
    await TestBed.inject(ApplicationRef).whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AiStatusService);
    TestBed.tick(); // starts the request
  });

  afterEach(() => backend.verify());

  it('is "loading" until the server answers', () => {
    expect(service.state()).toBe('loading');
    expect(service.loading()).toBe(true);
    expect(service.available()).toBe(false);
    backend.expectOne('/api/ai/status').flush({ available: true });
  });

  it('is "available" when the server says so', async () => {
    backend.expectOne('/api/ai/status').flush({ available: true });
    await settle();

    expect(service.state()).toBe('available');
    expect(service.available()).toBe(true);
  });

  it('is "key-missing" only when the server really answers that the AI is not available', async () => {
    backend.expectOne('/api/ai/status').flush({ available: false });
    await settle();

    expect(service.state()).toBe('key-missing');
    expect(service.available()).toBe(false);
  });

  it('is "unreachable", NOT "key-missing", when the proxy answers 502', async () => {
    backend.expectOne('/api/ai/status').flush('<html>Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' });
    await settle();

    expect(service.state()).toBe('unreachable');
    expect(service.available()).toBe(false);
  });

  it('is "unreachable" on a network error', async () => {
    backend.expectOne('/api/ai/status').error(new ProgressEvent('error'));
    await settle();

    expect(service.state()).toBe('unreachable');
  });

  it('asks again on retry() and recovers once the server is back', async () => {
    backend.expectOne('/api/ai/status').flush('<html>Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' });
    await settle();
    expect(service.state()).toBe('unreachable');

    service.retry();
    TestBed.tick();
    backend.expectOne('/api/ai/status').flush({ available: true });
    await settle();

    expect(service.state()).toBe('available');
  });
});
