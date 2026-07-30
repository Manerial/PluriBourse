import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { map } from 'rxjs';
import { resolveVolunteerLandingPath } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';
import { depositPhaseGuard } from '../../core/guards/deposit-phase.guard';
import { salePhaseGuard } from '../../core/guards/sale-phase.guard';

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
    path: 'pos',
    canActivate: [salePhaseGuard],
    loadComponent: () =>
      import('./pos/pos-page.component').then((m) => m.PosPageComponent),
  },
  {
    path: 'catalog',
    loadComponent: () =>
      import('../catalog/item-catalog.component').then((m) => m.ItemCatalogComponent),
  },
];
