/** Runtime configuration, read from EXPO_PUBLIC_* variables (see .env.example). */
export const env = {
  apiUrl: (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/$/, ''),
};
