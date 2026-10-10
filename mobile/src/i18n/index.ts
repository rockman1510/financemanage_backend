import { getLocales } from 'expo-localization';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';
import { vi } from './vi';

export const languages = ['vi', 'en'] as const;
export type Language = (typeof languages)[number];

function deviceLanguage(): Language {
  try {
    const code = getLocales()[0]?.languageCode;
    return code === 'en' ? 'en' : 'vi';
  } catch {
    return 'vi';
  }
}

if (!i18n.isInitialized) {
  // eslint-disable-next-line import/no-named-as-default-member -- official i18next setup pattern
  void i18n.use(initReactI18next).init({
    resources: { vi: { translation: vi }, en: { translation: en } },
    lng: deviceLanguage(),
    fallbackLng: 'vi',
    interpolation: { escapeValue: false },
  });
}

export default i18n;
