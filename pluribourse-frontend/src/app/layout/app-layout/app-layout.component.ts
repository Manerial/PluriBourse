import { Component, computed, DestroyRef, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TranslatePipe } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';
import { CurrentEditionService } from '../../services/current-edition.service';
import { SseService } from '../../services/sse.service';

// NOTE: MatIcon is NOT used — it requires MatIconRegistry configuration to work with Material Symbols.
// Use <span class="material-symbols-outlined"> directly instead (simpler, font loaded in index.html).
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, TranslatePipe, ToastContainerComponent],
  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss'
})
export class AppLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly sseService = inject(SseService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly isVolunteer = computed(() => this.auth.currentUser()?.role === 'VOLUNTEER');
  readonly currentEdition = this.currentEditionService.currentEdition;

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
