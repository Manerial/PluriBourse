import { HttpErrorResponse } from '@angular/common/http';

interface ProblemDetailBody {
  type?: string;
}

export function extractErrorType(error: HttpErrorResponse): string | undefined {
  return (error.error as ProblemDetailBody | null)?.type;
}

/**
 * Matches the repeated "422 with a specific problem-detail type suffix" guard scattered across
 * components (e.g. invalid-printer-selection, item-modification-locked) into a single reusable check.
 */
export function isErrorType(error: unknown, status: number, typeSuffix: string): boolean {
  return error instanceof HttpErrorResponse && error.status === status && (extractErrorType(error)?.endsWith(typeSuffix) ?? false);
}

export interface ConflictingItem {
  itemId: number;
  name: string;
}

interface BasketConflictBody extends ProblemDetailBody {
  conflictingItems?: ConflictingItem[];
}

export function extractConflictingItems(error: HttpErrorResponse): ConflictingItem[] | undefined {
  return (error.error as BasketConflictBody | null)?.conflictingItems;
}
