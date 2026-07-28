import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent],
      providers: [provideTranslateService({ lang: 'en' })],
    }).compileComponents();
  });

  it('renders icon and message', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput('message', 'No items found');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty-state__icon').textContent).toContain('group');
    expect(fixture.nativeElement.querySelector('.empty-state__message').textContent).toContain('No items found');
  });

  it('renders no action button when actionLabel is undefined', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput('message', 'Empty');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('renders action button when actionLabel is provided', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput('message', 'Empty');
    fixture.componentRef.setInput('actionLabel', 'Add item');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn.textContent).toContain('Add item');
  });

  it('emits action event when button is clicked', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput('message', 'Empty');
    fixture.componentRef.setInput('actionLabel', 'Add item');
    fixture.detectChanges();
    const actionSpy = vi.fn();
    fixture.componentInstance.action.subscribe(actionSpy);
    fixture.nativeElement.querySelector('button').click();
    expect(actionSpy).toHaveBeenCalledOnce();
  });

  it('disables the action button and shows a spinner when actionLoading is true', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput('message', 'Empty');
    fixture.componentRef.setInput('actionLabel', 'Add item');
    fixture.componentRef.setInput('actionLoading', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button');
    expect(btn.disabled).toBe(true);
    expect(btn.textContent).not.toContain('Add item');
    expect(fixture.nativeElement.querySelector('mat-progress-spinner')).not.toBeNull();
  });
});
