import { HttpErrorResponse } from '@angular/common/http';

interface ProblemDetailBody {
  type?: string;
}

export function extractErrorType(error: HttpErrorResponse): string | undefined {
  return (error.error as ProblemDetailBody | null)?.type;
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
