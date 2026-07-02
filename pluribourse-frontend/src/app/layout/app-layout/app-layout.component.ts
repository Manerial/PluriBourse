import { Component, computed, DestroyRef, effect, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';
import { CurrentEditionService } from '../../services/current-edition.service';
import { SseService } from '../../services/sse.service';
import { resolveVolunteerLandingPath } from '../../models/active-phase.enum';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, MatMenuModule, TranslatePipe, ToastContainerComponent],
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

  constructor() {
    // Skips the effect's own initial run (fired at construction, before ngOnInit's loadEdition()
    // resolves) so it never redirects on a stale/null phase — only on real changes afterwards,
    // whether that's the first load settling or a later SSE phase-changed event.
    let isFirstRun = true;
    effect(() => {
      const phase = this.currentEdition()?.phase;
      if (isFirstRun) {
        isFirstRun = false;
        return;
      }
      if (!this.isVolunteer()) {
        return;
      }
      const currentUrl = this.router.url;
      if (currentUrl !== '/404' && !currentUrl.startsWith('/volunteer')) {
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
}
