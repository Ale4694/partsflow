import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from './notification';
import { ProblemDetail } from './models';

/** Set this on a request when the caller shows the error itself (e.g. inside a form). */
export const SKIP_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);

export const UNREACHABLE_MESSAGE = 'Il server non è raggiungibile, riprova tra qualche secondo.';

/** What to tell the user for each "code" the AI endpoints send (the backend's own text is in English). */
const AI_MESSAGES: Record<string, string> = {
  AI_KEY_MISSING: 'Le funzioni AI non sono attive: sul server manca la chiave LLM_API_KEY.',
  AI_REJECTED:
    'Il servizio AI ha rifiutato la richiesta. Controlla la chiave e il modello configurati sul server.',
  AI_TEMPORARILY_UNAVAILABLE:
    'Il servizio AI è sovraccarico o ha esaurito la quota gratuita. Riprova tra un minuto.',
  AI_BAD_ANSWER: 'Il servizio AI ha risposto in modo non utilizzabile. Riprova.',
};

/** The ProblemDetail the backend sent, or null when the body is something else (an HTML page from the proxy...). */
function problemOf(error: HttpErrorResponse): ProblemDetail | null {
  const body: unknown = error.error;
  if (body !== null && typeof body === 'object' && ('detail' in body || 'title' in body || 'status' in body)) {
    return body as ProblemDetail;
  }
  return null;
}

/**
 * True when the application did not answer at all: a network error, or a 502/503/504 that has no ProblemDetail
 * (those come from the proxy in front of the backend, e.g. nginx while the backend is starting or down).
 * A 503 WITH a ProblemDetail is a real answer of the backend (for example "AI not configured").
 */
export function isServerUnreachable(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }
  return error.status === 0 || ([502, 503, 504].includes(error.status) && problemOf(error) === null);
}

/** The message to show for a failed call, in Italian when we know the cause, else the "detail" of the ProblemDetail. */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Errore imprevisto';
  }
  if (isServerUnreachable(error)) {
    return UNREACHABLE_MESSAGE;
  }
  const problem = problemOf(error);
  if (problem) {
    return (problem.code && AI_MESSAGES[problem.code]) || problem.detail || problem.title || `Errore ${error.status}`;
  }
  return `Errore ${error.status}`;
}

/** Turns every failed HTTP call into a visible message, then lets the error continue to the caller. */
export const problemDetailInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (!request.context.get(SKIP_ERROR_NOTIFICATION)) {
        notifications.error(problemMessage(error));
      }
      return throwError(() => error);
    }),
  );
};
