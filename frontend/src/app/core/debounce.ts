import { DestroyRef, InjectionToken, inject } from '@angular/core';

/** How long the user must stop typing before a search starts. A token so that tests can make it short. */
export const SEARCH_DEBOUNCE_MS = new InjectionToken<number>('SEARCH_DEBOUNCE_MS', {
  providedIn: 'root',
  factory: () => 300,
});

/**
 * Wraps a function so that it runs only after the user has stopped typing for the debounce time: one request per
 * pause instead of one per key. Must be called in an injection context (a component field); the pending call is
 * cancelled when the component is destroyed.
 */
export function debounced<T>(callback: (value: T) => void): (value: T) => void {
  const milliseconds = inject(SEARCH_DEBOUNCE_MS);
  let timer: ReturnType<typeof setTimeout> | undefined;
  inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  return (value: T) => {
    clearTimeout(timer);
    timer = setTimeout(() => callback(value), milliseconds);
  };
}
