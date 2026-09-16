import { Language } from './language.enum';

export interface GlobalInstanceConfigDto {
  associationName: string;
  // number, not BigDecimal string — see item.model.ts for why.
  defaultCommissionRate: number;
  defaultDocumentLanguage: Language;
  defaultCurrency: string;
}

