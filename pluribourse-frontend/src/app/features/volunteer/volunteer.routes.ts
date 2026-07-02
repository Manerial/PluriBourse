import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { map } from 'rxjs';
import { resolveVolunteerLandingPath } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';

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
    loadComponent: () =>
      import('./deposit/seller-search.component').then((m) => m.SellerSearchComponent),
  },
];
