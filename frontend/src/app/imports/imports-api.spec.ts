import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DraftStatus } from '../core/models';
import { ImportsApi } from './imports-api';

describe('ImportsApi', () => {
  let api: ImportsApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ImportsApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists drafts, filtered by status only when one is chosen', () => {
    const status = signal<DraftStatus | null>(null);
    TestBed.runInInjectionContext(() => api.list(status, () => 0));
    TestBed.tick();

    const all = backend.expectOne((r) => r.url === '/api/imports');
    expect(all.request.params.has('status')).toBe(false);
    all.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    status.set('DRAFT');
    TestBed.tick();
    const drafts = backend.expectOne((r) => r.url === '/api/imports');
    expect(drafts.request.params.get('status')).toBe('DRAFT');
    drafts.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('uploads an XML file as multipart form data under the name "file"', () => {
    const file = new File(['<xml/>'], 'invoice.xml', { type: 'text/xml' });
    api.uploadXml(file).subscribe();

    const request = backend.expectOne('/api/imports/fatturapa');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({});
  });

  it('resolves a line with the chosen item', () => {
    api.resolveLine(4, 12, 9).subscribe();

    const request = backend.expectOne('/api/imports/4/lines/12/resolve');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ itemId: 9 });
    request.flush({});
  });

  it('skips a line, confirms and discards a draft', () => {
    api.skipLine(4, 12).subscribe();
    const skip = backend.expectOne('/api/imports/4/lines/12/skip');
    expect(skip.request.method).toBe('POST');
    skip.flush({});

    api.confirm(4).subscribe();
    const confirm = backend.expectOne('/api/imports/4/confirm');
    expect(confirm.request.method).toBe('POST');
    confirm.flush({});

    api.discard(4).subscribe();
    const discard = backend.expectOne('/api/imports/4');
    expect(discard.request.method).toBe('DELETE');
    discard.flush(null);
  });
});
