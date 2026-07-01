import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { UserService } from './user.service';
import { CreateUserRequest, UserDto } from '../models/user.model';

const MOCK_USER: UserDto = { id: 1, firstName: 'Alice', lastName: 'Smith', username: 'alice', role: 'VOLUNTEER', enabled: true };

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getVolunteers() sends GET /api/admin/users', async () => {
    const p = firstValueFrom(service.getVolunteers());
    const req = http.expectOne('/api/admin/users');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_USER]);
    expect(await p).toEqual([MOCK_USER]);
  });

  it('createVolunteer() sends POST /api/admin/users', async () => {
    const payload: CreateUserRequest = { firstName: 'Alice', lastName: 'Smith', username: 'alice', password: 'Passw0rd!', role: 'VOLUNTEER' };
    const p = firstValueFrom(service.createVolunteer(payload));
    const req = http.expectOne('/api/admin/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(MOCK_USER);
    expect(await p).toEqual(MOCK_USER);
  });

  it('resetPassword() sends PUT /api/admin/users/1/reset-password', async () => {
    const p = firstValueFrom(service.resetPassword(1, 'NewPassw0rd!'));
    const req = http.expectOne('/api/admin/users/1/reset-password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ newPassword: 'NewPassw0rd!' });
    req.flush(null);
    await p;
  });

  it('disableVolunteer() sends PUT /api/admin/users/1/disable', async () => {
    const p = firstValueFrom(service.disableVolunteer(1));
    const req = http.expectOne('/api/admin/users/1/disable');
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    await p;
  });

  it('enableVolunteer() sends PUT /api/admin/users/1/enable', async () => {
    const p = firstValueFrom(service.enableVolunteer(1));
    const req = http.expectOne('/api/admin/users/1/enable');
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    await p;
  });

  it('deleteVolunteer() sends DELETE /api/admin/users/1', async () => {
    const p = firstValueFrom(service.deleteVolunteer(1));
    const req = http.expectOne('/api/admin/users/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await p;
  });
});
