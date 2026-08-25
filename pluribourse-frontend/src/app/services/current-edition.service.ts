import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, EMPTY, map, Observable, tap } from 'rxjs';
import { EditionDto, PhaseChangedEvent } from '../models/edition.model';

// Mirrors backend PhaseType.ACTIVE (Story 2.10) : PREPARATION n'est plus "active". Ce set n'est
// volontairement PAS dérivé de l'enum ActivePhase ci-dessous — celui-ci garde PREPARATION pour
// ALL_PHASES (ordre du dialogue de contrôle de phase, active-phase.enum.ts), un usage distinct.
const ACTIVE_PHASES = new Set<string>(['DEPOSIT', 'SALE', 'POST_SALE']);

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
    return this.http.get<EditionDto>('/api/editions/current').pipe(
      tap(edition => {
        if (requestSequence === this.latestSequence) {
          this.currentEdition.set(edition);
        }
      }),
      map(() => undefined),
      catchError((error: HttpErrorResponse) => {
        // The backend reports "no active edition" as 404 (no-active-edition), not a 2xx —
        // that's the routine case here and must still clear a stale signal.
        if (error.status === 404 && requestSequence === this.latestSequence) {
          this.currentEdition.set(null);
        }
        return EMPTY;
      })
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
