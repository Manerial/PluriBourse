import { Language } from '../models/language.enum';

export function detectBrowserLanguage(): Language {
  return navigator.language.toLowerCase().startsWith('fr') ? Language.FR : Language.EN;
}
