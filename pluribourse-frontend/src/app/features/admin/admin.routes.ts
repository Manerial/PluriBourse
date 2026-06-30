import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '',
    redirectTo: 'users',
    pathMatch: 'full'
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./users/user-list.component').then((m) => m.UserListComponent),
  },
  {
    path: 'users/create',
    loadComponent: () =>
      import('./users/user-form.component').then((m) => m.UserFormComponent),
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./settings/admin-settings.component').then((m) => m.AdminSettingsComponent),
  },
  {
    path: 'editions',
    loadComponent: () =>
      import('./editions/edition-list.component').then((m) => m.EditionListComponent),
  },
  {
    path: 'editions/:id/phase',
    loadComponent: () =>
      import('./editions/phase-control/phase-control.component').then((m) => m.PhaseControlComponent),
  },
  {
    path: 'editions/create',
    loadComponent: () =>
      import('./editions/edition-form.component').then((m) => m.EditionFormComponent),
  },
  {
    path: 'editions/:id/edit',
    loadComponent: () =>
      import('./editions/edition-form.component').then((m) => m.EditionFormComponent),
  },
  {
    path: 'editions/:id/categories',
    loadComponent: () =>
      import('./editions/edition-categories/edition-categories.component').then((m) => m.EditionCategoriesComponent),
  },
];
