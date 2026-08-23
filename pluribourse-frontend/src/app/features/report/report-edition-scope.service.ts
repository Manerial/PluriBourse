import { Injectable, signal } from '@angular/core';

// Provided at the 'reports' route level (see admin.routes.ts), not root: shares the admin's
// edition choice between ReportPageComponent (the selector) and the routed edition/exports tabs
// without threading it through router params, and resets on every navigation into the section.
@Injectable()
export class ReportEditionScopeService {
  readonly selectedEditionId = signal<number | null>(null);
}
