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
    path: 'sellers',
    loadComponent: () =>
      import('./sellers/seller-list.component').then((m) => m.SellerListComponent),
  },
  {
    path: 'print-queue',
    loadComponent: () =>
      import('./print-queue/print-queue-list.component').then((m) => m.PrintQueueListComponent),
  },
  {
    path: 'printers',
    loadComponent: () =>
      import('./printers/printer-list.component').then((m) => m.PrinterListComponent),
  },
];
