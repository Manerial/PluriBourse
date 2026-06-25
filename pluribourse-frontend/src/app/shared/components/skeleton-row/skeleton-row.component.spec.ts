import { TestBed } from '@angular/core/testing';
import { SkeletonRowComponent } from './skeleton-row.component';

describe('SkeletonRowComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonRowComponent],
    }).compileComponents();
  });

  it('renders 3 skeleton rows by default', () => {
    const fixture = TestBed.createComponent(SkeletonRowComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.skeleton-row').length).toBe(3);
  });

  it('renders the specified number of rows', () => {
    const fixture = TestBed.createComponent(SkeletonRowComponent);
    fixture.componentRef.setInput('rows', 5);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.skeleton-row').length).toBe(5);
  });

  it('container has aria-hidden=true', () => {
    const fixture = TestBed.createComponent(SkeletonRowComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.skeleton-list').getAttribute('aria-hidden')).toBe('true');
  });
});
