import apiClient from './client';
import type { UserProfile } from '../types/auth';

export interface UpdateProfileRequest {
  displayName?: string;
  defaultCurrency?: string;
  measurementUnit?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export const userApi = {
  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<UserProfile>('/user/profile', data).then((r) => r.data),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient.put('/user/password', data),
};
