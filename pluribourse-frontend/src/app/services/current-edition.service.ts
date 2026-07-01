import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, EMPTY, map, Observable, tap } from 'rxjs';
import { EditionDto, PhaseChangedEvent } from '../models/edition.model';
import { ActivePhase } from '../models/active-phase.enum';

const ACTIVE_PHASES = new Set<string>(Object.values(ActivePhase));

@Injectable({ providedIn: 'root' })
export class CurrentEditionService {
  private readonly http = inject(HttpClient);

  // Bumped by every state-changing operation — a load() and an optimistic SSE update alike —
  // so a resync that resolves after a newer update was already applied gets discarded instead
  // of overwriting it with stale data.
  private latestSequence = 0;

  readonly currentEdition = signal<EditionDto | null>(null);

  loadEdition(): Observable<void> {
    const requestSequence = ++this.latestSequence;
    return this.http.get<EditionDto>('/api/editions/current', { observe: 'response' }).pipe(
      tap(response => {
        if (requestSequence === this.latestSequence) {
          this.currentEdition.set(response.status === 200 ? response.body : null);
        }
      }),
      map(() => undefined),
      catchError(() => EMPTY)
    );
  }

  updateFromEvent(event: PhaseChangedEvent): void {
    const current = this.currentEdition();
    if (!ACTIVE_PHASES.has(event.newPhase)) {
      this.latestSequence++;
      this.currentEdition.set(null);
    } else if (current && current.id === event.editionId) {
      this.latestSequence++;
      this.currentEdition.set({ ...current, phase: event.newPhase });
    } else {
      this.loadEdition().subscribe();
    }
  }
}
