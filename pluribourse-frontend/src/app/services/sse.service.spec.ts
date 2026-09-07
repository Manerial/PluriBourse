import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { SseService } from './sse.service';
import { AuthService } from './auth.service';
import { CurrentEditionService } from './current-edition.service';
import { BasketCancelledEvent, PhaseChangedEvent } from '../models/edition.model';
import { SettlementUpdatedEvent } from '../models/settlement.model';

type MockEventSourceInstance = {
  addEventListener: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
  onerror: ((e: Event) => void) | null;
  readyState: number;
};

/**
 * Factory whose `ctor` mints a fresh instance on every `new EventSource(...)` and records them all
 * in `instances` — needed to assert that a dropped connection is followed by a brand-new EventSource
 * after the reconnect delay. Single-connection tests just read `instances[0]`.
 */
function createMockEventSourceFactory() {
  const instances: MockEventSourceInstance[] = [];
  // Regular function required — arrow functions cannot be used with `new`.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const ctor: any = vi.fn(function MockEventSource(this: any) {
    const instance: MockEventSourceInstance = {
      addEventListener: vi.fn(),
      close: vi.fn(),
      onerror: null,
      readyState: 0,
    };
    instances.push(instance);
    return instance;
  });
  ctor.CONNECTING = 0;
  ctor.OPEN = 1;
  ctor.CLOSED = 2;
  return { ctor, instances };
}

function listenerFor(instance: MockEventSourceInstance, eventName: string): ((event: unknown) => void) | undefined {
  return instance.addEventListener.mock.calls.find(call => call[0] === eventName)?.[1];
}

function fireError(instance: MockEventSourceInstance, readyState: number): void {
  instance.readyState = readyState;
  instance.onerror?.(new Event('error'));
}

describe('SseService', () => {
  let service: SseService;
  let router: Router;
  let httpTesting: HttpTestingController;
  let mockClearSession: ReturnType<typeof vi.fn>;
  let mockLoadEdition: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockClearSession = vi.fn();
    mockLoadEdition = vi.fn().mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { clearSession: mockClearSession } },
        { provide: CurrentEditionService, useValue: { loadEdition: mockLoadEdition } },
      ],
    });
    service = TestBed.inject(SseService);
    router = TestBed.inject(Router);
    httpTesting = TestBed.inject(HttpTestingController);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('creates EventSource with withCredentials: true', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    expect(ctor).toHaveBeenCalledWith('/api/sse/events', { withCredentials: true });

    subscription.unsubscribe();
    expect(instances[0].close).toHaveBeenCalledOnce();
  });

  it('emits parsed PhaseChangedEvent on phase-changed message', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const received: PhaseChangedEvent[] = [];
    const subscription = service.phaseChanges().subscribe(e => received.push(e));

    const handler = listenerFor(instances[0], 'phase-changed');
    const event: PhaseChangedEvent = { editionId: 1, newPhase: 'DEPOSIT', previousPhase: 'PREPARATION' };
    handler?.({ data: JSON.stringify(event) });

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual(event);

    subscription.unsubscribe();
  });

  it('ignores malformed JSON without erroring the Observable', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    let errored = false;
    const subscription = service.phaseChanges().subscribe({ error: () => { errored = true; } });

    const handler = listenerFor(instances[0], 'phase-changed');
    handler?.({ data: 'not-valid-json' });

    expect(errored).toBe(false);
    subscription.unsubscribe();
  });

  it('ignores a validly-parsed but non-object payload', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const received: PhaseChangedEvent[] = [];
    const subscription = service.phaseChanges().subscribe(e => received.push(e));

    const handler = listenerFor(instances[0], 'phase-changed');
    handler?.({ data: '42' });

    expect(received).toHaveLength(0);
    subscription.unsubscribe();
  });

  it('emits parsed BasketCancelledEvent on basket-cancelled message', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const received: BasketCancelledEvent[] = [];
    const subscription = service.basketCancelled().subscribe(e => received.push(e));

    const handler = listenerFor(instances[0], 'basket-cancelled');
    const event: BasketCancelledEvent = { editionId: 1, newPhase: 'DEPOSIT' };
    handler?.({ data: JSON.stringify(event) });

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual(event);

    subscription.unsubscribe();
  });

  it('emits parsed SettlementUpdatedEvent on settlement-updated message', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const received: SettlementUpdatedEvent[] = [];
    const subscription = service.settlementUpdated().subscribe(e => received.push(e));

    const handler = listenerFor(instances[0], 'settlement-updated');
    const event: SettlementUpdatedEvent = { editionId: 1, sellerId: 42 };
    handler?.({ data: JSON.stringify(event) });

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual(event);

    subscription.unsubscribe();
  });

  it('ignores a settlement-updated payload missing a numeric sellerId', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const received: SettlementUpdatedEvent[] = [];
    const subscription = service.settlementUpdated().subscribe(e => received.push(e));

    const handler = listenerFor(instances[0], 'settlement-updated');
    handler?.({ data: JSON.stringify({ editionId: 1 }) });

    expect(received).toHaveLength(0);
    subscription.unsubscribe();
  });

  it('closes EventSource on unsubscribe', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    subscription.unsubscribe();

    expect(instances[0].close).toHaveBeenCalledOnce();
  });

  it('does nothing on a transient reconnect (readyState still CONNECTING)', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    fireError(instances[0], ctor.CONNECTING);

    expect(mockClearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(instances[0].close).not.toHaveBeenCalled();
    subscription.unsubscribe();
  });

  it('does not log out on a permanent failure (readyState CLOSED) — it closes and reconnects', () => {
    vi.useFakeTimers();
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    fireError(instances[0], ctor.CLOSED);

    expect(mockClearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(instances[0].close).toHaveBeenCalledOnce();

    vi.advanceTimersByTime(8000);
    expect(instances).toHaveLength(2);

    subscription.unsubscribe();
  });

  it('probes /api/auth/me exactly once after two consecutive failures, then keeps reconnecting on a 2xx', () => {
    vi.useFakeTimers();
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();

    fireError(instances[0], ctor.CLOSED);            // failure 1 — no probe
    httpTesting.expectNone('/api/auth/me');
    vi.advanceTimersByTime(8000);                    // reconnect -> instances[1]

    fireError(instances[1], ctor.CLOSED);            // failure 2 — one probe
    const probe = httpTesting.expectOne('/api/auth/me');
    probe.flush({}, { status: 200, statusText: 'OK' });

    vi.advanceTimersByTime(8000);                    // reconnect -> instances[2]
    expect(instances).toHaveLength(3);

    fireError(instances[2], ctor.CLOSED);            // failure 1 again — counter was reset
    httpTesting.expectNone('/api/auth/me');

    subscription.unsubscribe();
  });

  it('resyncs the edition on (re)connect for phaseChanges, but not for the other streams', () => {
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const phaseSub = service.phaseChanges().subscribe();
    listenerFor(instances[0], 'open')?.(new Event('open'));
    expect(mockLoadEdition).toHaveBeenCalledTimes(1);
    phaseSub.unsubscribe();

    mockLoadEdition.mockClear();

    const basketSub = service.basketCancelled().subscribe();
    listenerFor(instances[1], 'open')?.(new Event('open'));
    const settlementSub = service.settlementUpdated().subscribe();
    listenerFor(instances[2], 'open')?.(new Event('open'));
    expect(mockLoadEdition).not.toHaveBeenCalled();

    basketSub.unsubscribe();
    settlementSub.unsubscribe();
  });

  it('cancels a pending reconnect timer on teardown', () => {
    vi.useFakeTimers();
    const { ctor, instances } = createMockEventSourceFactory();
    vi.stubGlobal('EventSource', ctor);

    const subscription = service.phaseChanges().subscribe();
    fireError(instances[0], ctor.CLOSED);   // schedules a reconnect
    subscription.unsubscribe();             // must clear the pending timer

    vi.advanceTimersByTime(20000);
    expect(instances).toHaveLength(1);      // no extra EventSource created
  });
});
