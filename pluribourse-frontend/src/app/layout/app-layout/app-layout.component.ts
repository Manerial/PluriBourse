import { Component, computed, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, filter, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';
import { CurrentEditionService } from '../../services/current-edition.service';
import { SseService } from '../../services/sse.service';
import { PrintService } from '../../services/print.service';
import { PhaseType } from '../../models/edition.model';
import { PrinterSelectionStatus } from '../../models/printer.model';
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
  private readonly printService = inject(PrintService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly isVolunteer = computed(() => this.auth.currentUser()?.role === 'VOLUNTEER');
  readonly currentEdition = this.currentEditionService.currentEdition;
  readonly sidebarCollapsed = signal(readSidebarCollapsed(this.auth.currentUser()?.username ?? ''));

  // null = not loaded yet (nothing shown, avoids a flash of the warning at boot); once loaded,
  // a null id on the DTO itself means that printer type is genuinely missing from the session.
  readonly printerSelectionStatus = signal<PrinterSelectionStatus | null>(null);
  readonly missingThermalPrinter = computed(() => this.printerSelectionStatus()?.thermalPrinterId === null);
  readonly missingA4Printer = computed(() => this.printerSelectionStatus()?.a4PrinterId === null);

  // i18n key for the topbar printer warning tooltip, or null when nothing is missing — keeps the
  // three-way message choice (thermal / A4 / both) out of the template.
  readonly printerWarningKey = computed<string | null>(() => {
    const thermalMissing = this.missingThermalPrinter();
    const a4Missing = this.missingA4Printer();
    if (thermalMissing && a4Missing) {
      return 'nav.printerWarning.bothMissing';
    }
    if (thermalMissing) {
      return 'nav.printerWarning.thermalMissing';
    }
    if (a4Missing) {
      return 'nav.printerWarning.a4Missing';
    }
    return null;
  });

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

    if (this.isVolunteer()) {
      // startWith(null) triggers the initial load through the same pipeline as the refetch-on-
      // navigation below; switchMap cancels any still-in-flight request when a new one starts, so
      // a slow response to an earlier navigation can never resolve after (and overwrite) a faster
      // one to a later navigation — same class of race CurrentEditionService.loadEdition() guards
      // against with its own sequence counter, solved here with the rxjs-native equivalent.
      this.router.events.pipe(
        filter((navigationEvent): navigationEvent is NavigationEnd => navigationEvent instanceof NavigationEnd),
        startWith(null),
        switchMap(() => this.printService.getSelection().pipe(
          // Fail-open, same posture as CurrentEditionService.loadEdition(): a network hiccup here
          // must not surface as a blocking error for a purely informational warning.
          catchError(() => of(null))
        )),
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(status => this.printerSelectionStatus.set(status));
    }
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
