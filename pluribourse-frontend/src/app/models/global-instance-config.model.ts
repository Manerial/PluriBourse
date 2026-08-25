import { Language } from './language.enum';

export interface GlobalInstanceConfigDto {
  associationName: string;
  defaultCommissionRate: number;
  defaultDocumentLanguage: Language;
  defaultCurrency: string;
}

