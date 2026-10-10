# Mobile (React Native / Expo)

Expo SDK 57 · Expo Router · TypeScript strict · TanStack Query · Zustand · React Hook Form + Zod · i18next ·
expo-secure-store · Jest (jest-expo) + React Native Testing Library.

```bash
cp .env.example .env     # EXPO_PUBLIC_API_URL trỏ tới backend
npm install
npx expo start           # mở bằng Expo Go hoặc emulator
npm test                 # unit/component test
npm run typecheck && npm run lint
```

## Cấu trúc

```
src/
├─ app/                    Routes Expo Router: (auth)/login, (auth)/register, (tabs)/index|deposits|loans|settings,
│                          deposits/new|[id], loans/new|[id], notifications – route chỉ re-export màn hình
├─ features/<feature>/     api.ts (gọi REST) · hooks.ts (React Query) · schemas.ts (Zod + mapping form→request)
│                          screens/ · components/ · __tests__/
├─ shared/api/             client.ts (JSON, bearer, refresh token khi 401, ApiError), types.ts (hợp đồng API), queryKeys
├─ shared/ui/              Design system: theme tokens (light/dark), Button, TextField, Card, SegmentedControl…
├─ shared/session/         Zustand store lưu token trong Keychain/Keystore
├─ shared/config/          env + feature flags
└─ i18n/                   vi (mặc định) / en
```

## Customize

- Đổi thương hiệu: sửa `src/shared/ui/theme.ts`.
- Bật/tắt tính năng: `EXPO_PUBLIC_FEATURE_LOAN_PREPAYMENT`, `EXPO_PUBLIC_FEATURE_LOAN_RATE_CHANGE`.
- Thêm ngôn ngữ: thêm file trong `src/i18n/` (kiểu `Translations` bắt buộc đủ key).
