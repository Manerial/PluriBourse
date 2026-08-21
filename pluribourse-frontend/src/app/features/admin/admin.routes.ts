import { Routes } from '@angular/router';
import { settlementPhaseGuard } from '../../core/guards/settlement-phase.guard';

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
  {
    path: 'catalog',
    loadComponent: () =>
      import('../catalog/item-catalog.component').then((m) => m.ItemCatalogComponent),
  },
  {
    path: 'archived-catalog',
    loadComponent: () =>
      import('./archived-catalog/archived-catalog.component').then((m) => m.ArchivedCatalogComponent),
  },
  {
    path: 'settlement',
    canActivate: [settlementPhaseGuard],
    loadComponent: () =>
      import('../settlement/settlement-list.component').then((m) => m.SettlementListComponent),
  },
  {
    path: 'reports',
    loadComponent: () =>
      import('../report/report-page.component').then((m) => m.ReportPageComponent),
  },
];
