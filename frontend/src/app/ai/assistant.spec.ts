import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Assistant } from './assistant';

describe('Assistant page', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<Assistant>;

  /** Opens the page and answers the AI status request with the given response. */
  async function open(answer: (request: ReturnType<HttpTestingController['expectOne']>) => void): Promise<void> {
    fixture = TestBed.createComponent(Assistant);
    fixture.detectChanges(); // starts the HTTP request
    answer(backend.expectOne('/api/ai/status'));
    await fixture.whenStable();
  }

  const text = () => fixture.nativeElement.textContent as string;
  const sendButton = () =>
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (button) => button.textContent?.trim() === 'Invia',
    );

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('says the key is missing only when the server answered that the AI is not available', async () => {
    await open((request) => request.flush({ available: false }));

    expect(text()).toContain('Assistente non disponibile');
    expect(text()).toContain('LLM_API_KEY');
    expect(sendButton()?.disabled).toBe(true);
  });

  it('says the server is unreachable (and does not blame the key) on a 502 from the proxy', async () => {
    await open((request) =>
      request.flush('<html>Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' }),
    );

    expect(text()).toContain('Il server non è raggiungibile, riprova tra qualche secondo.');
    expect(text()).not.toContain('LLM_API_KEY');
    expect(text()).not.toContain('Bad Gateway');
    expect(sendButton()?.disabled).toBe(true);
  });

  it('shows no warning when the AI is available', async () => {
    await open((request) => request.flush({ available: true }));

    expect(text()).not.toContain('non disponibile');
    expect(text()).not.toContain('non è raggiungibile');
  });
});
