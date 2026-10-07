import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Draft, DraftLine } from '../core/models';
import { DraftDetail } from './draft-detail';

function line(overrides: Partial<DraftLine>): DraftLine {
  return {
    id: 1,
    lineNumber: 1,
    supplierCode: 'RR-BRK-001',
    description: 'Front brake pad set',
    quantity: 10,
    unit: 'PZ',
    unitPrice: 25,
    totalPrice: 250,
    vatRate: 22,
    stockDelta: 10,
    status: 'MATCHED',
    itemId: 1,
    itemCode: 'BRK-001',
    ...overrides,
  };
}

function draft(overrides: Partial<Draft>): Draft {
  return {
    id: 7,
    status: 'DRAFT',
    source: 'XML',
    supplierId: 1,
    supplierName: 'Ricambi Rossi Srl',
    tipoDocumento: 'TD01',
    number: 'FT-0001/2026',
    date: '2026-03-10',
    total: 621.1,
    createdAt: '2026-03-10T09:30:00Z',
    confirmedAt: null,
    ddtReferences: [],
    pendingLines: 0,
    lines: [line({})],
    ...overrides,
  };
}

const PENDING_LINE = line({
  id: 2,
  lineNumber: 2,
  supplierCode: 'UNK-777',
  description: 'Timing belt kit',
  status: 'PENDING_REVIEW',
  itemId: null,
  itemCode: null,
});
const SKIPPED_LINE = line({ id: 3, lineNumber: 3, quantity: null, stockDelta: null, status: 'SKIPPED', itemId: null, itemCode: null });

describe('DraftDetail', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<DraftDetail>;

  /** Opens draft 7 and answers the two requests the screen makes (the draft and the AI status). */
  async function open(response: Draft): Promise<void> {
    fixture = TestBed.createComponent(DraftDetail);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges(); // starts the HTTP requests
    backend.expectOne('/api/imports/7').flush(response);
    backend.expectOne('/api/ai/status').flush({ available: false });
    await fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement | undefined {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return buttons.find((b) => b.textContent?.trim() === label);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('keeps "Conferma" disabled while a line is pending review', async () => {
    await open(draft({ pendingLines: 1, lines: [line({}), PENDING_LINE] }));

    expect(button('Conferma')?.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Per confermare, abbina o ignora tutte le righe');
  });

  it('enables "Conferma" when no line is pending', async () => {
    await open(draft({ pendingLines: 0, lines: [line({}), SKIPPED_LINE] }));

    expect(button('Conferma')?.disabled).toBe(false);
  });

  it('enables "Conferma" after the last pending line is skipped', async () => {
    await open(draft({ pendingLines: 1, lines: [line({}), PENDING_LINE] }));
    expect(button('Conferma')?.disabled).toBe(true);

    button('Ignora')?.click();
    const request = backend.expectOne('/api/imports/7/lines/2/skip');
    expect(request.request.method).toBe('POST');
    request.flush(draft({ pendingLines: 0, lines: [line({}), { ...PENDING_LINE, status: 'SKIPPED' }] }));
    await fixture.whenStable();

    expect(button('Conferma')?.disabled).toBe(false);
  });

  it('shows a confirmed draft without the confirm and discard buttons', async () => {
    await open(draft({ status: 'CONFIRMED', confirmedAt: '2026-03-11T08:00:00Z' }));

    expect(button('Conferma')).toBeUndefined();
    expect(button('Scarta bozza')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('Confermata');
  });

  it('colour-codes the status of each line', async () => {
    await open(draft({ pendingLines: 1, lines: [line({}), PENDING_LINE, SKIPPED_LINE] }));

    const badge = (text: string) =>
      (Array.from(fixture.nativeElement.querySelectorAll('app-line-status .badge')) as HTMLElement[]).find(
        (element) => element.textContent?.trim() === text,
      );
    expect(badge('Abbinata')?.classList).toContain('badge-success');
    expect(badge('Da verificare')?.classList).toContain('badge-warning');
    expect(badge('Ignorata')?.classList.contains('badge-success')).toBe(false);
    expect(badge('Ignorata')?.classList.contains('badge-warning')).toBe(false);
  });

  it('shows the header data of the document', async () => {
    await open(draft({ ddtReferences: [{ number: 'DDT-0042', date: '2026-03-09' }] }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('TD01 n. FT-0001/2026');
    expect(text).toContain('Ricambi Rossi Srl');
    expect(text).toContain('10/03/2026');
    expect(text).toContain('DDT-0042');
  });
});
