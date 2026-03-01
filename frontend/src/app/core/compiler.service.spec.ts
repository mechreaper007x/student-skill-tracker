import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CompilerService } from './compiler.service';

describe('CompilerService telemetry endpoints', () => {
  let service: CompilerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(CompilerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should post compile attempt with failure context fields', () => {
    service.recordRishiCompileAttempt(42, {
      success: false,
      source: 'leetcode_submit',
      errorMessage: 'wrong answer',
      testsPassed: 10,
      testsTotal: 14,
      failedTestInput: '[1,2,3]',
      expectedOutput: '4',
      actualOutput: '5',
      stackTraceSnippet: 'AssertionError'
    }).subscribe();

    const req = httpMock.expectOne('/api/rishi/coding/sessions/42/compile');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.testsTotal).toBe(14);
    expect(req.request.body.failedTestInput).toBe('[1,2,3]');
    expect(req.request.body.stackTraceSnippet).toBe('AssertionError');
    req.flush({
      status: 'recorded',
      success: false,
      source: 'leetcode_submit',
      failureBucket: 'TEST_FAILURE',
      mistakeCategory: 'WRONG_ANSWER',
      accuracyPct: 71.4,
      summary: 'Program ran but produced incorrect output.',
      nextSteps: ['Compare expected vs actual output.'],
      recordedAt: new Date().toISOString()
    });
  });

  it('should post end session with detailed activity durations', () => {
    service.endRishiCodingSession(99, {
      reason: 'battle_station_closed',
      activeDurationMs: 45000,
      typingDurationMs: 30000,
      cursorIdleDurationMs: 15000,
      editorUnfocusedDurationMs: 5000,
      tabHiddenDurationMs: 2000
    }).subscribe();

    const req = httpMock.expectOne('/api/rishi/coding/sessions/99/end');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.typingDurationMs).toBe(30000);
    expect(req.request.body.cursorIdleDurationMs).toBe(15000);
    expect(req.request.body.editorUnfocusedDurationMs).toBe(5000);
    expect(req.request.body.tabHiddenDurationMs).toBe(2000);
    req.flush({ status: 'ended' });
  });
});

