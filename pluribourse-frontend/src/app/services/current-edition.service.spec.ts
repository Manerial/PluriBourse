import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { CurrentEditionService } from './current-edition.service';
import { EditionDto } from '../models/edition.model';
import { Language } from '../models/language.enum';

const mockEdition: EditionDto = {
  id: 1,
  name: 'Bourse 2026',
  phase: 'PREPARATION',
  commissionRate: 15,
  documentLanguage: Language.FR,
  createdAt: '2026-01-01',
  archived: false,
  startDate: null,
  endDate: null,
};

describe('CurrentEditionService', () => {
  let service: CurrentEditionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CurrentEditionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => expect(service).toBeTruthy());

  it('initializes currentEdition to null', () => {
    expect(service.currentEdition()).toBeNull();
  });

  describe('load()', () => {
    it('sets currentEdition when server returns 200', () => {
      service.loadEdition().subscribe();
      const req = httpMock.expectOne('/api/editions/current');
      req.flush(mockEdition, { status: 200, statusText: 'OK' });
      expect(service.currentEdition()).toEqual(mockEdition);
    });

    it('sets currentEdition to null when server returns 204', () => {
      service.currentEdition.set(mockEdition);
      service.loadEdition().subscribe();
      const req = httpMock.expectOne('/api/editions/current');
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(service.currentEdition()).toBeNull();
    });

    it('completes gracefully on HTTP error without throwing', () => {
      service.currentEdition.set(mockEdition);
      let errored = false;
      service.loadEdition().subscribe({ error: () => { errored = true; } });
      const req = httpMock.expectOne('/api/editions/current');
      req.flush('server error', { status: 500, statusText: 'Internal Server Error' });
      expect(errored).toBe(false);
      expect(service.currentEdition()).toEqual(mockEdition);
    });

    it('discards a stale in-flight response when a newer load() has since resolved', () => {
      service.loadEdition().subscribe();
      const firstReq = httpMock.expectOne('/api/editions/current');

      service.loadEdition().subscribe();
      const secondReq = httpMock.expectOne('/api/editions/current');

      secondReq.flush({ ...mockEdition, id: 2 }, { status: 200, statusText: 'OK' });
      firstReq.flush(mockEdition, { status: 200, statusText: 'OK' });

      expect(service.currentEdition()?.id).toBe(2);
    });
  });

  describe('updateFromEvent()', () => {
    it('updates phase in-place when editionId matches', () => {
      service.currentEdition.set(mockEdition);
      service.updateFromEvent({ editionId: 1, newPhase: 'DEPOSIT', previousPhase: 'PREPARATION' });
      expect(service.currentEdition()?.phase).toBe('DEPOSIT');
      expect(service.currentEdition()?.id).toBe(1);
    });

    it('sets currentEdition to null when newPhase is CLOSED', () => {
      service.currentEdition.set(mockEdition);
      service.updateFromEvent({ editionId: 1, newPhase: 'CLOSED', previousPhase: 'POST_SALE' });
      expect(service.currentEdition()).toBeNull();
    });

    it('calls load() (subscribed internally) when editionId does not match current (resync)', () => {
      service.currentEdition.set(mockEdition);
      service.updateFromEvent({ editionId: 99, newPhase: 'DEPOSIT', previousPhase: 'PREPARATION' });
      const req = httpMock.expectOne('/api/editions/current');
      req.flush({ ...mockEdition, id: 99, phase: 'DEPOSIT' }, { status: 200, statusText: 'OK' });
      expect(service.currentEdition()?.id).toBe(99);
    });

    it('does not let a stale in-flight resync overwrite a newer optimistic update', () => {
      // A resync is triggered while currentEdition is still null (e.g. right after login).
      service.updateFromEvent({ editionId: 1, newPhase: 'DEPOSIT', previousPhase: 'PREPARATION' });
      const staleReq = httpMock.expectOne('/api/editions/current');

      // Before that resync resolves, the app finishes loading the edition and a second,
      // faster phase change arrives and applies optimistically (DEPOSIT -> SALE).
      service.currentEdition.set(mockEdition);
      service.updateFromEvent({ editionId: 1, newPhase: 'SALE', previousPhase: 'DEPOSIT' });

      // The stale resync (still carrying DEPOSIT) resolves last and must be discarded.
      staleReq.flush({ ...mockEdition, phase: 'DEPOSIT' }, { status: 200, statusText: 'OK' });

      expect(service.currentEdition()?.phase).toBe('SALE');
    });
  });
});
