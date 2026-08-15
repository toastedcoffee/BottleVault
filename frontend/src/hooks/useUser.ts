// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useMutation } from '@tanstack/react-query';
import { userApi, type UpdateProfileRequest, type ChangePasswordRequest } from '../api/user.api';

export function useUpdateProfile() {
  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => userApi.updateProfile(data),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (data: ChangePasswordRequest) => userApi.changePassword(data),
  });
}
