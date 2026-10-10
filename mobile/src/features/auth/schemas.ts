import { z } from 'zod';

/** Error messages are i18n keys, translated when displayed. */
export const loginSchema = z.object({
  email: z.string().trim().email('auth.invalidEmail'),
  password: z.string().min(1, 'common.required'),
});

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, 'common.required').max(100),
  email: z.string().trim().email('auth.invalidEmail'),
  password: z.string().min(8, 'auth.passwordTooShort').max(72),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
