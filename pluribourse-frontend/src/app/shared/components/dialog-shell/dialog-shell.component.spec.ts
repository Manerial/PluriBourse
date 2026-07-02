import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideTranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DialogShellComponent } from './dialog-shell.component';

@Component({
  standalone: true,
  imports: [DialogShellComponent],
  template: `<app-dialog-shell title="Test title" (close)="onClose()"><p class="projected">content</p></app-dialog-shell>`,
})
class HostComponent {
  closed = false;
  onClose(): void { this.closed = true; }
}

describe('DialogShellComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideTranslateService({ lang: 'en' })],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the title', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.dialog-shell__title');
    expect(el.textContent).toContain('Test title');
  });

  it('projects content', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.projected');
    expect(el.textContent).toBe('content');
  });

  it('emits close when the close button is clicked', () => {
    fixture.debugElement.query(By.css('.dialog-shell__close')).nativeElement.click();
    expect(fixture.componentInstance.closed).toBe(true);
  });
});
