import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { PhaseChangedEvent, PhaseType } from '../models/edition.model';
import { ALL_PHASES } from '../models/active-phase.enum';
import { AuthService } from './auth.service';
import { CurrentEditionService } from './current-edition.service';

const PHASE_VALUES = new Set<string>(ALL_PHASES);

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

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly currentEditionService = inject(CurrentEditionService);

  phaseChanges(): Observable<PhaseChangedEvent> {
    return new Observable(observer => {
      const source = new EventSource('/api/sse/events', { withCredentials: true });
      source.addEventListener('phase-changed', (event: MessageEvent) => {
        try {
          const parsed: unknown = JSON.parse(event.data);
          if (isPhaseChangedEvent(parsed)) {
            observer.next(parsed);
          }
        } catch {
          // malformed event — ignore
        }
      });
      source.onerror = () => {
        // A normal reconnect leaves readyState at CONNECTING and the browser retries on its own.
        // CLOSED means the browser gave up for good (e.g. the reconnect attempt got a 401) —
        // treat it the same way authInterceptor treats an expired session.
        if (source.readyState === EventSource.CLOSED) {
          this.auth.clearSession();
          this.currentEditionService.currentEdition.set(null);
          this.router.navigate(['/login']);
        }
      };
      return () => source.close();
    });
  }
}
