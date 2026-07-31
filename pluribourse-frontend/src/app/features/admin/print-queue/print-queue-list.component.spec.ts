import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PrintQueueListComponent } from './print-queue-list.component';
import { PrintQueueService } from '../../../services/print-queue.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { PrinterStatus } from '../../../models/printer-status.model';

const CONNECTED_PRINTER: PrinterStatus = {
  id: 1,
  name: 'Thermique Guichet',
  type: 'THERMAL',
  connected: true,
  queueDepth: 0,
  jobInProgress: false,
  lastError: null,
  canRetry: false,
};

const FAILED_PRINTER: PrinterStatus = {
  id: 2,
  name: 'A4 Bureau',
  type: 'A4',
  connected: false,
  queueDepth: 2,
  jobInProgress: false,
  lastError: 'bourrage papier',
  canRetry: true,
};

const DISCONNECTED_PRINTER: PrinterStatus = {
  id: 3,
  name: 'Thermique Reserve',
  type: 'THERMAL',
  connected: false,
  queueDepth: 0,
  jobInProgress: false,
  lastError: 'Cannot connect to 127.0.0.1:1',
  canRetry: false,
};

describe('PrintQueueListComponent', () => {
  let fixture: ComponentFixture<PrintQueueListComponent>;
  let component: PrintQueueListComponent;

  const printQueueServiceMock = {
    getStatuses: vi.fn().mockReturnValue(of([CONNECTED_PRINTER, FAILED_PRINTER, DISCONNECTED_PRINTER])),
    refreshStatuses: vi.fn().mockReturnValue(of([CONNECTED_PRINTER, FAILED_PRINTER, DISCONNECTED_PRINTER])),
    resumeQueue: vi.fn().mockReturnValue(of(undefined)),
    discardFailedJob: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    printQueueServiceMock.getStatuses.mockReturnValue(of([CONNECTED_PRINTER, FAILED_PRINTER, DISCONNECTED_PRINTER]));
    printQueueServiceMock.refreshStatuses.mockReturnValue(of([CONNECTED_PRINTER, FAILED_PRINTER, DISCONNECTED_PRINTER]));
    printQueueServiceMock.resumeQueue.mockReturnValue(of(undefined));
    printQueueServiceMock.discardFailedJob.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [PrintQueueListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PrintQueueService, useValue: printQueueServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PrintQueueListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads printer statuses on init from the cached endpoint, not a live check', () => {
    expect(printQueueServiceMock.getStatuses).toHaveBeenCalledTimes(1);
    expect(printQueueServiceMock.refreshStatuses).not.toHaveBeenCalled();
    expect(component.statuses().length).toBe(3);
    expect(component.error()).toBeNull();
  });

  it('sets a dedicated error key when refresh() fails', async () => {
    printQueueServiceMock.refreshStatuses.mockReturnValue(throwError(() => new Error('network')));
    await component.refresh();
    expect(component.error()).toBe('admin.printQueue.error.refresh');
  });

  it('refresh() live-checks connectivity via refreshStatuses(), not the cached getStatuses()', async () => {
    printQueueServiceMock.getStatuses.mockClear();
    await component.refresh();
    expect(printQueueServiceMock.refreshStatuses).toHaveBeenCalledOnce();
    expect(printQueueServiceMock.getStatuses).not.toHaveBeenCalled();
  });

  it('renders one card per printer with the connection chip state', () => {
    fixture.detectChanges();
    const chips: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.connection-chip');
    expect(chips.length).toBe(3);
    expect(chips[0].classList.contains('connection-chip--job-error')).toBe(false);
    expect(chips[0].classList.contains('connection-chip--offline')).toBe(false);
    expect(chips[1].classList.contains('connection-chip--job-error')).toBe(true);
    expect(chips[1].classList.contains('connection-chip--offline')).toBe(false);
    expect(chips[2].classList.contains('connection-chip--job-error')).toBe(false);
    expect(chips[2].classList.contains('connection-chip--offline')).toBe(true);
  });

  it('renders the printer type through the i18n pipe instead of the raw enum value', () => {
    fixture.detectChanges();
    const types: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.printer-card__type');
    // No translation loader is wired in this test bed, so the pipe resolves to the untranslated
    // key — asserting on that key (rather than "THERMAL"/"A4" directly) proves the template goes
    // through `| translate` instead of interpolating the raw enum value.
    expect(types[0].textContent?.trim()).toBe('admin.printQueue.type.THERMAL');
    expect(types[1].textContent?.trim()).toBe('admin.printQueue.type.A4');
  });

  it('connectionState distinguishes a reachable printer with a failed job from a genuinely unreachable one', () => {
    expect(component.connectionState(CONNECTED_PRINTER)).toBe('connected');
    expect(component.connectionState(FAILED_PRINTER)).toBe('jobError');
    expect(component.connectionState(DISCONNECTED_PRINTER)).toBe('disconnected');
  });

  it('shows an inline error banner only for printers with a lastError', () => {
    fixture.detectChanges();
    const cards: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.printer-card');
    expect(cards[0].querySelector('.notification')).toBeNull();
    expect(cards[1].querySelector('.notification')).not.toBeNull();
    expect(cards[2].querySelector('.notification')).not.toBeNull();
  });

  it('shows Resume/Discard buttons only when canRetry is true', () => {
    fixture.detectChanges();
    const cards: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.printer-card');
    expect(cards[0].querySelector('.printer-card__actions')).toBeNull();
    expect(cards[1].querySelector('.printer-card__actions')).not.toBeNull();
    expect(cards[2].querySelector('.printer-card__actions')).toBeNull();
  });

  it('shows an empty state when no printer is registered', async () => {
    printQueueServiceMock.refreshStatuses.mockReturnValue(of([]));
    await component.refresh();
    fixture.detectChanges();
    expect(component.statuses().length).toBe(0);
  });

  it('resume calls the service and shows a success toast, then reloads', async () => {
    await component.resume(FAILED_PRINTER);
    expect(printQueueServiceMock.resumeQueue).toHaveBeenCalledWith(2);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(printQueueServiceMock.getStatuses).toHaveBeenCalledTimes(2);
  });

  it('shows an error toast when resume fails, and still reloads to reflect the printer\'s real current state', async () => {
    printQueueServiceMock.resumeQueue.mockReturnValue(throwError(() => new Error('server')));
    await component.resume(FAILED_PRINTER);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(printQueueServiceMock.getStatuses).toHaveBeenCalledTimes(2);
  });

  it('discard calls the service and shows a success toast, then reloads', async () => {
    await component.discard(FAILED_PRINTER);
    expect(printQueueServiceMock.discardFailedJob).toHaveBeenCalledWith(2);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(printQueueServiceMock.getStatuses).toHaveBeenCalledTimes(2);
  });

  it('shows an error toast when discard fails, and still reloads to reflect the printer\'s real current state', async () => {
    printQueueServiceMock.discardFailedJob.mockReturnValue(throwError(() => new Error('server')));
    await component.discard(FAILED_PRINTER);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(printQueueServiceMock.getStatuses).toHaveBeenCalledTimes(2);
  });

  it('shows the loading skeleton while statuses are being fetched', () => {
    expect(component.isLoading()).toBe(false);
    component.isLoading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-skeleton-row')).not.toBeNull();
  });
});
