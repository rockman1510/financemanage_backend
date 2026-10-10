import '@/i18n';
import i18n from 'i18next';
import { mockRouter } from '@/test/router';

// Tests assert on Vietnamese copy (default language).
void i18n.changeLanguage('vi');

jest.mock('expo-router', () => {
  const React = require('react');
  const { mockRouter } = require('@/test/router');
  const Stack = Object.assign(() => null, { Screen: () => null, Protected: ({ children }: any) => children });
  return {
    useRouter: () => mockRouter,
    useLocalSearchParams: jest.fn(() => ({})),
    Link: ({ children }: { children: any }) => React.createElement(React.Fragment, null, children),
    Stack,
  };
});

jest.mock('@/shared/storage/secureStorage', () => {
  const store = new Map<string, string>();
  return {
    secureStorage: {
      getItem: jest.fn(async (key: string) => store.get(key) ?? null),
      setItem: jest.fn(async (key: string, value: string) => void store.set(key, value)),
      removeItem: jest.fn(async (key: string) => void store.delete(key)),
      __store: store,
    },
  };
});

beforeEach(() => {
  mockRouter.push.mockClear();
  mockRouter.replace.mockClear();
  mockRouter.back.mockClear();
});
