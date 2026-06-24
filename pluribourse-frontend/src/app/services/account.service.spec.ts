import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { AccountService } from './account.service';
import { Language } from '../models/language.enum';

describe('AccountService', () => {
  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AccountService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('updateLanguage', () => {
    it('sends PUT to /api/account/language-preference with language as query param', async () => {
      const promise = firstValueFrom(service.updateLanguage(Language.EN));
      const req = httpMock.expectOne('/api/account/language-preference?language=EN');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toBeNull();
      req.flush(null);
      await promise;
    });
  });
});
