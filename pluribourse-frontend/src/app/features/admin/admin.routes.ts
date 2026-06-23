import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: 'users',
    loadComponent: () => import('./users/user-list.component').then(m => m.UserListComponent)
  },
  {
    path: 'users/create',
    loadComponent: () => import('./users/user-form.component').then(m => m.UserFormComponent)
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./settings/admin-settings.component').then(m => m.AdminSettingsComponent)
  }
];
