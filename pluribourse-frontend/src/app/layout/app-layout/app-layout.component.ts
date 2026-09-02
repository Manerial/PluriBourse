import { Component, computed, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';
import { CurrentEditionService } from '../../services/current-edition.service';
import { SseService } from '../../services/sse.service';
import { PhaseType } from '../../models/edition.model';
import { resolveVolunteerLandingPath } from '../../models/active-phase.enum';

const SIDEBAR_COLLAPSED_KEY_PREFIX = 'pluribourse.sidebarCollapsed.';

// Volunteer pages the phase-change auto-redirect (below) is allowed to bounce the user away
// from. Anything else under /volunteer (e.g. /volunteer/catalog, usable in every phase per
// FR-083) must be left alone, exactly like /account already is.
const PHASE_BOUND_VOLUNTEER_PATHS = ['/volunteer/deposit', '/volunteer/pos', '/volunteer/sales', '/volunteer/settlement'];

// localStorage throws in private-browsing/storage-disabled contexts — must not break the layout.
function readSidebarCollapsed(username: string): boolean {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY_PREFIX + username) === 'true';
  } catch {
    return false;
  }
}

function writeSidebarCollapsed(username: string, collapsed: boolean): void {
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY_PREFIX + username, String(collapsed));
  } catch {
    // Non-critical: the preference just won't persist across reloads.
  }
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule, TranslatePipe, ToastContainerComponent],
  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss'
})
export class AppLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly sseService = inject(SseService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly isVolunteer = computed(() => this.auth.currentUser()?.role === 'VOLUNTEER');
  readonly currentEdition = this.currentEditionService.currentEdition;
  readonly sidebarCollapsed = signal(readSidebarCollapsed(this.auth.currentUser()?.username ?? ''));

  constructor() {
    // Skips the effect's own initial run (fired at construction, before ngOnInit's loadEdition()
    // resolves) so it never redirects on a stale/null phase — only on real changes afterwards,
    // whether that's the first load settling or a later SSE phase-changed event.
    //
    // Gated on an actual phase *transition*: CurrentEditionService.loadEdition() re-emits a fresh
    // (but phase-identical) edition object on every call, and the phase guards call it on each
    // navigation. Without the previousPhase check, that re-emission would bounce a volunteer off
    // a valid same-phase page (e.g. /volunteer/sales → /volunteer/pos) every time they opened it.
    let previousPhase: PhaseType | undefined;
    let isFirstRun = true;
    effect(() => {
      const phase = this.currentEdition()?.phase;
      if (isFirstRun) {
        isFirstRun = false;
        previousPhase = phase;
        return;
      }
      if (phase === previousPhase) {
        return;
      }
      previousPhase = phase;
      if (!this.isVolunteer()) {
        return;
      }
      const currentUrl = this.router.url;
      const isPhaseBound = currentUrl === '/404' || currentUrl === '/printer-selection' ||
        PHASE_BOUND_VOLUNTEER_PATHS.includes(currentUrl);
      if (!isPhaseBound) {
        return;
      }
      const target = resolveVolunteerLandingPath(phase);
      if (currentUrl !== target) {
        this.router.navigateByUrl(target);
      }
    });
  }

  ngOnInit(): void {
    this.currentEditionService.loadEdition().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe();

    this.sseService.phaseChanges().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(event => this.currentEditionService.updateFromEvent(event));
  }

  async logout(): Promise<void> {
    await this.auth.logout();
  }

  toggleSidebar(): void {
    const collapsed = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(collapsed);
    writeSidebarCollapsed(this.auth.currentUser()?.username ?? '', collapsed);
  }
}
