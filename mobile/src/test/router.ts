/** Shared expo-router mock used by jest.setup.ts and tests that assert navigation. */
export const mockRouter = { push: jest.fn(), replace: jest.fn(), back: jest.fn() };
