import { HttpErrorResponse } from '@angular/common/http';

interface ProblemDetailBody {
  type?: string;
}

export function extractErrorType(error: HttpErrorResponse): string | undefined {
  return (error.error as ProblemDetailBody | null)?.type;
}
