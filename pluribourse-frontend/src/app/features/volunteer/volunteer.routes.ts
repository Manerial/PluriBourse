import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { map } from 'rxjs';
import { resolveVolunteerLandingPath } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';
import { depositPhaseGuard } from '../../core/guards/deposit-phase.guard';

export const volunteerRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => {
      const currentEditionService = inject(CurrentEditionService);
      return currentEditionService.loadEdition().pipe(
        map(() => resolveVolunteerLandingPath(currentEditionService.currentEdition()?.phase))
      );
    },
  },
  {
    path: 'deposit',
    canActivate: [depositPhaseGuard],
    loadComponent: () =>
      import('./deposit/deposit-page.component').then((m) => m.DepositPageComponent),
  },
  {
    path: 'catalog',
    loadComponent: () =>
      import('../catalog/item-catalog.component').then((m) => m.ItemCatalogComponent),
  },
];
