import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => vi.useRealTimers());

  it('shows an error and a success message', () => {
    service.error('Qualcosa non va');
    service.success('Fatto');

    expect(service.notifications().map((n) => [n.kind, n.message])).toEqual([
      ['error', 'Qualcosa non va'],
      ['success', 'Fatto'],
    ]);
  });

  it('shows an identical message only once while it is visible', () => {
    service.error('Il server non è raggiungibile');
    service.error('Il server non è raggiungibile');
    service.error('Il server non è raggiungibile');

    expect(service.notifications()).toHaveLength(1);
  });

  it('keeps different messages apart', () => {
    service.error('Primo');
    service.error('Secondo');

    expect(service.notifications()).toHaveLength(2);
  });

  it('removes a message after a while and then shows the same one again', () => {
    service.error('Ancora');
    vi.advanceTimersByTime(9000);
    expect(service.notifications()).toHaveLength(0);

    service.error('Ancora');
    expect(service.notifications()).toHaveLength(1);
  });

  it('can be dismissed by hand', () => {
    service.success('Fatto');
    service.dismiss(service.notifications()[0].id);

    expect(service.notifications()).toHaveLength(0);
  });
});
