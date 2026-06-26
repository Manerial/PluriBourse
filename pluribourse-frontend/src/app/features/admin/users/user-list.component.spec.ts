import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { UserListComponent } from './user-list.component';
import { UserService } from '../../../services/user.service';
import { UserDto } from '../../../models/user.model';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ResetPasswordDialogComponent } from './reset-password-dialog/reset-password-dialog.component';

const MOCK_VOLUNTEERS: UserDto[] = [
  { id: 1, firstName: 'Alice', lastName: 'Smith', username: 'alice', role: 'VOLUNTEER', enabled: true },
  { id: 2, firstName: 'Bob', lastName: 'Dupont', username: 'bob', role: 'VOLUNTEER', enabled: false }
];

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let component: UserListComponent;

  const userServiceMock = {
    getVolunteers: vi.fn().mockReturnValue(of(MOCK_VOLUNTEERS)),
    disableVolunteer: vi.fn().mockReturnValue(of(undefined)),
    enableVolunteer: vi.fn().mockReturnValue(of(undefined)),
    resetPassword: vi.fn().mockReturnValue(of(undefined))
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  const dialogMock = {
    open: vi.fn().mockReturnValue({ closed: of('NewPassword1') })
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    userServiceMock.getVolunteers.mockReturnValue(of(MOCK_VOLUNTEERS));
    dialogMock.open.mockReturnValue({ closed: of('NewPassword1') });

    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: UserService, useValue: userServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: Dialog, useValue: dialogMock },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UserListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders the volunteer list on init', () => {
    expect(userServiceMock.getVolunteers).toHaveBeenCalledTimes(1);
    expect(component.users().length).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('shows error key when load fails', async () => {
    userServiceMock.getVolunteers.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('admin.users.error.load');
  });

  it('disable button calls disableVolunteer when user is enabled', async () => {
    const enabledUser = MOCK_VOLUNTEERS[0];
    await component.toggleEnabled(enabledUser);
    expect(userServiceMock.disableVolunteer).toHaveBeenCalledWith(1);
  });

  it('shows success toast after disabling a user', async () => {
    await component.toggleEnabled(MOCK_VOLUNTEERS[0]);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('enable button calls enableVolunteer when user is disabled', async () => {
    const disabledUser = MOCK_VOLUNTEERS[1];
    await component.toggleEnabled(disabledUser);
    expect(userServiceMock.enableVolunteer).toHaveBeenCalledWith(2);
  });

  it('shows success toast after enabling a user', async () => {
    await component.toggleEnabled(MOCK_VOLUNTEERS[1]);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('shows error toast when toggle fails', async () => {
    userServiceMock.disableVolunteer.mockReturnValue(throwError(() => new Error('server')));
    await component.toggleEnabled(MOCK_VOLUNTEERS[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
  });

  it('opens reset dialog with correct user name', async () => {
    component.openResetPasswordDialog(MOCK_VOLUNTEERS[0]);
    await fixture.whenStable();
    expect(dialogMock.open).toHaveBeenCalledWith(
      ResetPasswordDialogComponent,
      expect.objectContaining({ data: { userName: 'Alice Smith' } })
    );
  });

  it('calls resetPassword API and shows success toast after dialog confirms', async () => {
    component.openResetPasswordDialog(MOCK_VOLUNTEERS[0]);
    await fixture.whenStable();
    expect(userServiceMock.resetPassword).toHaveBeenCalledWith(1, 'NewPassword1');
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.submitting()).toBe(false);
  });

  it('does not call resetPassword when dialog is cancelled', async () => {
    dialogMock.open.mockReturnValueOnce({ closed: of(undefined) });
    component.openResetPasswordDialog(MOCK_VOLUNTEERS[0]);
    await fixture.whenStable();
    expect(userServiceMock.resetPassword).not.toHaveBeenCalled();
  });

  it('shows error toast when resetPassword API fails', async () => {
    userServiceMock.resetPassword.mockReturnValueOnce(throwError(() => new Error('server')));
    component.openResetPasswordDialog(MOCK_VOLUNTEERS[0]);
    await fixture.whenStable();
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });
});
