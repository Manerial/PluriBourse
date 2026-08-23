import { Component } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { PrintersPageComponent } from './printers-page.component';

@Component({ standalone: true, template: 'registry-stub' })
class RegistryStubComponent {}

@Component({ standalone: true, template: 'queue-stub' })
class QueueStubComponent {}

describe('PrintersPageComponent', () => {
  let fixture: ComponentFixture<PrintersPageComponent>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PrintersPageComponent],
      providers: [
        provideRouter([
          {
            path: 'admin/printers',
            component: PrintersPageComponent,
            children: [
              { path: '', redirectTo: 'registry', pathMatch: 'full' },
              { path: 'registry', component: RegistryStubComponent },
              { path: 'queue', component: QueueStubComponent },
            ],
          },
        ]),
        provideTranslateService({ lang: 'en' }),
      ],
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      admin: { printers: { tabs: { registry: 'Registry', queue: 'Print queue' } } },
    });

    router = TestBed.inject(Router);
  });

  async function navigate(path: string): Promise<void> {
    await router.navigateByUrl(path);
    fixture = TestBed.createComponent(PrintersPageComponent);
    fixture.detectChanges();
  }

  it('redirects to the registry tab by default and renders its content', async () => {
    await navigate('/admin/printers');
    expect(router.url).toBe('/admin/printers/registry');
    expect(fixture.nativeElement.textContent).toContain('registry-stub');
  });

  it('renders both tab links', async () => {
    await navigate('/admin/printers');
    const links: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[mat-tab-link]'));
    const labels = links.map(el => el.textContent?.trim());
    expect(labels).toContain('Registry');
    expect(labels).toContain('Print queue');
  });

  it('navigating to the queue tab swaps the router-outlet content', async () => {
    await navigate('/admin/printers/queue');
    expect(fixture.nativeElement.textContent).toContain('queue-stub');
    expect(fixture.nativeElement.textContent).not.toContain('registry-stub');
  });
});
