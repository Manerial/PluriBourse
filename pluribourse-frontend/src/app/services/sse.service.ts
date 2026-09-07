import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BasketCancelledEvent, PhaseChangedEvent, PhaseType } from '../models/edition.model';
import { SettlementUpdatedEvent } from '../models/settlement.model';
import { ALL_PHASES } from '../models/active-phase.enum';
import { CurrentEditionService } from './current-edition.service';

const PHASE_VALUES = new Set<string>(ALL_PHASES);

// Application-level reconnect: a fixed base delay plus random jitter (4–7 s) so a backend restart
// does not have every tab reconnect on the same tick. Exponential backoff is deferred to V2 — at
// this scale (one RPi 4, a handful of tills) the keepalive already makes drops rare.
const RECONNECT_BASE_MS = 4000;
const RECONNECT_JITTER_MS = 3000;
// After this many consecutive failed reconnects with no successful `open` in between, probe the
// session once through HttpClient (which, unlike EventSource, runs authInterceptor).
const PROBE_AFTER_FAILURES = 2;

function isPhaseType(value: unknown): value is PhaseType {
  return typeof value === 'string' && PHASE_VALUES.has(value);
}

function isPhaseChangedEvent(value: unknown): value is PhaseChangedEvent {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['editionId'] === 'number'
    && isPhaseType(candidate['newPhase'])
    && isPhaseType(candidate['previousPhase']);
}

function isBasketCancelledEvent(value: unknown): value is BasketCancelledEvent {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['editionId'] === 'number' && isPhaseType(candidate['newPhase']);
}

function isSettlementUpdatedEvent(value: unknown): value is SettlementUpdatedEvent {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['editionId'] === 'number' && typeof candidate['sellerId'] === 'number';
}

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly http = inject(HttpClient);
  private readonly currentEditionService = inject(CurrentEditionService);

  phaseChanges(): Observable<PhaseChangedEvent> {
    // Only the phase stream resyncs on (re)connect: after any gap the phase chip could be stale and
    // there is no replay of missed events. basketCancelled / settlementUpdated consumers already
    // refresh their own view, so they pass no onReconnect.
    return this.listen('phase-changed', isPhaseChangedEvent, () => this.currentEditionService.loadEdition().subscribe());
  }

  basketCancelled(): Observable<BasketCancelledEvent> {
    return this.listen('basket-cancelled', isBasketCancelledEvent);
  }

  settlementUpdated(): Observable<SettlementUpdatedEvent> {
    return this.listen('settlement-updated', isSettlementUpdatedEvent);
  }

  /**
   * Opens an EventSource on the shared SSE endpoint and keeps it alive across transport failures.
   *
   * A transport error (readyState CLOSED) never logs the user out here: EventSource bypasses
   * authInterceptor, so a CLOSED socket cannot be told apart from a genuine 401 without asking. The
   * handler closes the source and schedules an application-level reconnect (fixed delay + jitter).
   * After PROBE_AFTER_FAILURES consecutive failures with no successful `open` in between it fires
   * exactly one GET /api/auth/me: that request does run authInterceptor, so a 401/403 redirects to
   * /login there, while a 2xx resets the failure counter and reconnection continues.
   *
   * @param onReconnect run on every successful `open` (initial connection included) — used by the
   *                    phase stream to resync the phase chip.
   */
  private listen<T>(eventName: string, isValid: (value: unknown) => value is T, onReconnect?: () => void): Observable<T> {
    return new Observable<T>(observer => {
      let source: EventSource | null = null;
      let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
      let consecutiveFailures = 0;
      let disposed = false;

      const scheduleReconnect = (): void => {
        if (disposed || reconnectTimer) {
          return;
        }
        const delay = RECONNECT_BASE_MS + Math.random() * RECONNECT_JITTER_MS;
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          connect();
        }, delay);
      };

      const connect = (): void => {
        source = new EventSource('/api/sse/events', { withCredentials: true });

        source.addEventListener('open', () => {
          consecutiveFailures = 0;
          onReconnect?.();
        });

        source.addEventListener(eventName, (event: MessageEvent) => {
          try {
            const parsed: unknown = JSON.parse(event.data);
            if (isValid(parsed)) {
              observer.next(parsed);
            }
          } catch {
            // malformed event — ignore
          }
        });

        source.onerror = () => {
          if (disposed || !source || source.readyState !== EventSource.CLOSED) {
            // CONNECTING: the browser is retrying natively — nothing to do.
            return;
          }
          source.close();
          source = null;
          consecutiveFailures++;
          if (consecutiveFailures === PROBE_AFTER_FAILURES) {
            // Exactly once at the threshold: a 2xx resets the counter, so the next probe only goes
            // out after PROBE_AFTER_FAILURES more failures — not one probe per failed reconnect.
            this.probeSession(() => {
              consecutiveFailures = 0;
            });
          }
          scheduleReconnect();
        };
      };

      connect();

      return () => {
        disposed = true;
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
        }
        source?.close();
      };
    });
  }

  /**
   * One lightweight session check that goes through HttpClient — and therefore authInterceptor. A
   * 401/403 is handled entirely inside the interceptor (clearSession + navigate to /login), which
   * tears down AppLayoutComponent and with it the SSE subscription. A 2xx means the session is still
   * valid, so `onStillValid` resets the failure counter and reconnection keeps going.
   */
  private probeSession(onStillValid: () => void): void {
    this.http.get('/api/auth/me').subscribe({
      next: () => onStillValid(),
      error: () => {
        // 401/403 already routed by authInterceptor; nothing to do here.
      },
    });
  }
}
