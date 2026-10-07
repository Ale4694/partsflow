import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from './notification';
import { ProblemDetail } from './models';

/** Set this on a request when the caller shows the error itself (e.g. inside a form). */
export const SKIP_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);

/** The message to show for a failed call: the "detail" of the ProblemDetail the backend sent. */
export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Server non raggiungibile. Controlla che l’applicazione sia avviata.';
    }
    const problem = error.error as ProblemDetail | null;
    if (problem && typeof problem === 'object') {
      return problem.detail || problem.title || `Errore ${error.status}`;
    }
    return error.statusText && error.statusText !== 'OK' ? error.statusText : `Errore ${error.status}`;
  }
  return 'Errore imprevisto';
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
