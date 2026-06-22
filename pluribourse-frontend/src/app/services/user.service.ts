import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateUserRequest, UserDto } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  getVolunteers(): Observable<UserDto[]> {
    return this.http.get<UserDto[]>('/api/admin/users');
  }

  createVolunteer(data: CreateUserRequest): Observable<UserDto> {
    return this.http.post<UserDto>('/api/admin/users', data);
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/reset-password`, { newPassword });
  }

  disableVolunteer(id: number): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/disable`, {});
  }

  enableVolunteer(id: number): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/enable`, {});
  }
}
