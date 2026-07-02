import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { UserListComponent } from './user-list.component';
import { UserService } from '../../../services/user.service';
import { UserDto } from '../../../models/user.model';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { ResetPasswordDialogComponent } from './reset-password-dialog/reset-password-dialog.component';
import { UserFormComponent } from './user-form.component';

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
    resetPassword: vi.fn().mockReturnValue(of(undefined)),
    deleteVolunteer: vi.fn().mockReturnValue(of(undefined)),
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  const dialogMock = {
    open: vi.fn().mockReturnValue({ closed: of('NewPassword1') })
  };

  const confirmDialogMock = {
    open: vi.fn().mockReturnValue(of(false))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    userServiceMock.getVolunteers.mockReturnValue(of(MOCK_VOLUNTEERS));
    dialogMock.open.mockReturnValue({ closed: of('NewPassword1') });
    confirmDialogMock.open.mockReturnValue(of(false));

    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: UserService, useValue: userServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: Dialog, useValue: dialogMock },
        { provide: ConfirmDialogService, useValue: confirmDialogMock },
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

  it('openCreateDialog opens UserFormComponent', () => {
    component.openCreateDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(UserFormComponent, expect.anything());
  });

  it('reloads the user list after the create dialog closes', async () => {
    dialogMock.open.mockReturnValueOnce({ closed: of(undefined) });
    userServiceMock.getVolunteers.mockClear();
    component.openCreateDialog();
    await fixture.whenStable();
    expect(userServiceMock.getVolunteers).toHaveBeenCalledTimes(1);
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

  it('does not call deleteVolunteer when user cancels the confirm dialog', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(false));
    await component.confirmDelete(MOCK_VOLUNTEERS[0]);
    expect(userServiceMock.deleteVolunteer).not.toHaveBeenCalled();
  });

  it('calls deleteVolunteer and removes user from list after confirm', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    await component.confirmDelete(MOCK_VOLUNTEERS[0]);
    expect(userServiceMock.deleteVolunteer).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.users().find(u => u.id === 1)).toBeUndefined();
    expect(component.submitting()).toBe(false);
  });

  it('shows error toast when deleteVolunteer API fails', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    userServiceMock.deleteVolunteer.mockReturnValueOnce(throwError(() => new Error('server')));
    await component.confirmDelete(MOCK_VOLUNTEERS[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });
});
