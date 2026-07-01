export type Role = 'ADMIN' | 'VOLUNTEER' | 'SELLER';

export interface UserDto {
  id: number;
  firstName: string;
  lastName: string;
  username: string;
  role: Role;
  enabled: boolean;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  username: string;
  password: string;
  role: Role;
}
