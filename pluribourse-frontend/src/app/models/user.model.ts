export interface UserDto {
  id: number;
  firstName: string;
  lastName: string;
  username: string;
  role: string;
  enabled: boolean;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  username: string;
  password: string;
}
