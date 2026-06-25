import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';

// NOTE: MatIcon is NOT used — it requires MatIconRegistry configuration to work with Material Symbols.
// Use <span class="material-symbols-outlined"> directly instead (simpler, font loaded in index.html).
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, ToastContainerComponent],
  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss'
})
export class AppLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');

  async logout(): Promise<void> {
    await this.auth.logout();
  }
}
