import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { GlobalInstanceConfigService } from './global-instance-config.service';
import { GlobalInstanceConfigDto } from '../models/global-instance-config.model';

const MOCK_CONFIG: GlobalInstanceConfigDto = {
  associationName: 'Mon Asso',
  defaultCommissionRate: 20,
  defaultDocumentLanguage: 'EN'
};

describe('GlobalInstanceConfigService', () => {
  let service: GlobalInstanceConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(GlobalInstanceConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('getConfig', () => {
    it('sends GET to /api/admin/instance-config and returns config', async () => {
      const promise = firstValueFrom(service.getConfig());
      httpMock.expectOne('/api/admin/instance-config').flush(MOCK_CONFIG);
      const result = await promise;
      expect(result).toEqual(MOCK_CONFIG);
    });
  });

  describe('updateConfig', () => {
    it('sends PUT to /api/admin/instance-config with payload and returns updated config', async () => {
      const update: GlobalInstanceConfigDto = {
        associationName: 'Nouvelle Asso',
        defaultCommissionRate: 15,
        defaultDocumentLanguage: 'FR'
      };
      const updated: GlobalInstanceConfigDto = { ...update };

      const promise = firstValueFrom(service.updateConfig(update));
      const req = httpMock.expectOne('/api/admin/instance-config');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(update);
      req.flush(updated);
      const result = await promise;
      expect(result).toEqual(updated);
    });
  });
});
