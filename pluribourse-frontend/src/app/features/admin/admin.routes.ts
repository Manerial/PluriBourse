import { Routes } from '@angular/router';
import { settlementPhaseGuard } from '../../core/guards/settlement-phase.guard';
import { activeEditionGuard } from '../../core/guards/active-edition.guard';
import { ReportEditionScopeService } from '../report/report-edition-scope.service';

export const adminRoutes: Routes = [
  {
    path: '',
    redirectTo: 'settings',
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
    canActivate: [activeEditionGuard],
    loadComponent: () =>
      import('./sellers/seller-list.component').then((m) => m.SellerListComponent),
  },
  {
    path: 'printers',
    loadComponent: () =>
      import('./printers/printers-page.component').then((m) => m.PrintersPageComponent),
    children: [
      {
        path: '',
        redirectTo: 'registry',
        pathMatch: 'full',
      },
      {
        path: 'registry',
        loadComponent: () =>
          import('./printers/printer-list.component').then((m) => m.PrinterListComponent),
      },
      {
        path: 'queue',
        loadComponent: () =>
          import('./print-queue/print-queue-list.component').then((m) => m.PrintQueueListComponent),
      },
    ],
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
    providers: [ReportEditionScopeService],
    loadComponent: () =>
      import('../report/report-page.component').then((m) => m.ReportPageComponent),
    children: [
      {
        path: '',
        redirectTo: 'edition',
        pathMatch: 'full',
      },
      {
        path: 'edition',
        loadComponent: () =>
          import('../report/edition-report.component').then((m) => m.EditionReportComponent),
      },
      {
        path: 'exports',
        loadComponent: () =>
          import('../report/report-exports.component').then((m) => m.ReportExportsComponent),
      },
    ],
  },
];
