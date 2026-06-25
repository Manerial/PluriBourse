import { TestBed } from '@angular/core/testing';
import { NotificationInlineComponent } from './notification-inline.component';

describe('NotificationInlineComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationInlineComponent],
    }).compileComponents();
  });

  it('renders message text', () => {
    const fixture = TestBed.createComponent(NotificationInlineComponent);
    fixture.componentRef.setInput('message', 'Something went wrong');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notification__message').textContent).toContain('Something went wrong');
  });

  it('uses warning variant by default', () => {
    const fixture = TestBed.createComponent(NotificationInlineComponent);
    fixture.componentRef.setInput('message', 'Warning msg');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.notification');
    expect(el.classList).not.toContain('notification--error');
  });

  it('applies error class for error variant', () => {
    const fixture = TestBed.createComponent(NotificationInlineComponent);
    fixture.componentRef.setInput('message', 'Error msg');
    fixture.componentRef.setInput('variant', 'error');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notification--error')).not.toBeNull();
  });

  it('uses role=status and aria-live=polite for warning', () => {
    const fixture = TestBed.createComponent(NotificationInlineComponent);
    fixture.componentRef.setInput('message', 'Warn');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.notification');
    expect(el.getAttribute('role')).toBe('status');
    expect(el.getAttribute('aria-live')).toBe('polite');
  });

  it('uses role=alert and aria-live=assertive for error variant', () => {
    const fixture = TestBed.createComponent(NotificationInlineComponent);
    fixture.componentRef.setInput('message', 'Err');
    fixture.componentRef.setInput('variant', 'error');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.notification');
    expect(el.getAttribute('role')).toBe('alert');
    expect(el.getAttribute('aria-live')).toBe('assertive');
  });
});
