import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SellerSearchComponent } from './seller-search.component';
import { SellerService } from '../../../services/seller.service';
import { SellerDto } from '../../../models/seller.model';

const MOCK_SELLER: SellerDto = { id: 1, firstName: 'Pierre', lastName: 'Martin', email: 'martin.pierre@email.com', phone: '0612345678' };

describe('SellerSearchComponent', () => {
  let fixture: ComponentFixture<SellerSearchComponent>;
  let component: SellerSearchComponent;

  const sellerServiceMock = {
    search: vi.fn().mockReturnValue(of([])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    sellerServiceMock.search.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [SellerSearchComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: SellerService, useValue: sellerServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerSearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('focuses the search input on init (UX-DR15)', () => {
    expect(document.activeElement).toBe(component.searchInput()?.nativeElement);
  });

  it('does not call search when the query is blank', async () => {
    component.searchControl.setValue('');
    await fixture.whenStable();
    expect(sellerServiceMock.search).not.toHaveBeenCalled();
  });

  it('calls search on every keystroke and stores results', async () => {
    sellerServiceMock.search.mockReturnValue(of([MOCK_SELLER]));
    component.searchControl.setValue('martin');
    await fixture.whenStable();
    expect(sellerServiceMock.search).toHaveBeenCalledWith('martin');
    expect(component.results()).toEqual([MOCK_SELLER]);
    expect(component.searched()).toBe(true);
  });

  it('shows create button when search yields no results', async () => {
    component.searchControl.setValue('unknown');
    await fixture.whenStable();
    expect(component.searched()).toBe(true);
    expect(component.results().length).toBe(0);
  });

  it('sets error key when search fails', async () => {
    sellerServiceMock.search.mockReturnValue(throwError(() => new Error('server')));
    component.searchControl.setValue('martin');
    await fixture.whenStable();
    expect(component.error()).toBe('volunteer.deposit.error.search');
  });

  it('selectSeller() selects the seller and clears the search state', async () => {
    sellerServiceMock.search.mockReturnValue(of([MOCK_SELLER]));
    component.searchControl.setValue('martin');
    await fixture.whenStable();

    component.selectSeller(MOCK_SELLER);

    expect(component.selectedSeller()).toEqual(MOCK_SELLER);
    expect(component.results()).toEqual([]);
    expect(component.searched()).toBe(false);
  });

  it('changeSeller() clears the selection', () => {
    component.selectSeller(MOCK_SELLER);
    component.changeSeller();
    expect(component.selectedSeller()).toBeNull();
  });

  it('openCreateForm()/cancelCreateForm() toggle the create form', () => {
    component.openCreateForm();
    expect(component.showCreateForm()).toBe(true);
    component.cancelCreateForm();
    expect(component.showCreateForm()).toBe(false);
  });

  it('onSellerCreated() selects the newly created seller and hides the form (AC4)', () => {
    component.openCreateForm();
    component.onSellerCreated(MOCK_SELLER);
    expect(component.showCreateForm()).toBe(false);
    expect(component.selectedSeller()).toEqual(MOCK_SELLER);
  });
});
