import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { SseService } from './sse.service';
import { AuthService } from './auth.service';
import { PhaseChangedEvent } from '../models/edition.model';

type MockEventSourceInstance = {
  addEventListener: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
  onerror: ((e: Event) => void) | null;
  readyState: number;
};

function createMockEventSource() {
  const instance: MockEventSourceInstance = {
    addEventListener: vi.fn(),
    close: vi.fn(),
    onerror: null,
    readyState: 0,
  };
  // Regular function required — arrow functions cannot be used with `new`
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const ctor: any = vi.fn(function MockEventSource(this: any) { return instance; });
  ctor.CONNECTING = 0;
  ctor.OPEN = 1;
  ctor.CLOSED = 2;
  return { ctor, instance };
}

describe('SseService', () => {
  let service: SseService;
  let router: Router;
  let mockClearSession: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockClearSession = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { clearSession: mockClearSession } },
      ],
    });
    service = TestBed.inject(SseService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('creates EventSource with withCredentials: true', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    expect(ctor).toHaveBeenCalledWith('/api/sse/events', { withCredentials: true });

    subscription.unsubscribe();
    expect(instance.close).toHaveBeenCalledOnce();
  });

  it('emits parsed PhaseChangedEvent on phase-changed message', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);

    const received: PhaseChangedEvent[] = [];
    const subscription = service.phaseChanges().subscribe(e => received.push(e));

    const handler = instance.addEventListener.mock.calls.find(c => c[0] === 'phase-changed')?.[1];
    const event: PhaseChangedEvent = { editionId: 1, newPhase: 'DEPOSIT', previousPhase: 'PREPARATION' };
    handler({ data: JSON.stringify(event) });

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual(event);

    subscription.unsubscribe();
  });

  it('ignores malformed JSON without erroring the Observable', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);

    let errored = false;
    const subscription = service.phaseChanges().subscribe({ error: () => { errored = true; } });

    const handler = instance.addEventListener.mock.calls.find(c => c[0] === 'phase-changed')?.[1];
    handler({ data: 'not-valid-json' });

    expect(errored).toBe(false);
    subscription.unsubscribe();
  });

  it('ignores a validly-parsed but non-object payload', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);

    const received: PhaseChangedEvent[] = [];
    const subscription = service.phaseChanges().subscribe(e => received.push(e));

    const handler = instance.addEventListener.mock.calls.find(c => c[0] === 'phase-changed')?.[1];
    handler({ data: '42' });

    expect(received).toHaveLength(0);
    subscription.unsubscribe();
  });

  it('closes EventSource on unsubscribe', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    subscription.unsubscribe();

    expect(instance.close).toHaveBeenCalledOnce();
  });

  it('does nothing on a transient reconnect (readyState still CONNECTING)', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);
    instance.readyState = 0; // CONNECTING

    const subscription = service.phaseChanges().subscribe();
    instance.onerror?.(new Event('error'));

    expect(mockClearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    subscription.unsubscribe();
  });

  it('clears the session and redirects to /login on permanent failure (readyState CLOSED)', () => {
    const { ctor, instance } = createMockEventSource();
    vi.stubGlobal('EventSource', ctor);
    instance.readyState = 2; // CLOSED

    const subscription = service.phaseChanges().subscribe();
    instance.onerror?.(new Event('error'));

    expect(mockClearSession).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    subscription.unsubscribe();
  });
});
