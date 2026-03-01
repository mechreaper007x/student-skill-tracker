import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CompilationResult {
  success: boolean;
  output: string;
  error: string;
  executionTime: string;
  language: string;
  timestamp: string;
}

export interface CompilerInfo {
  languageName: string;
  languageVersion: string;
  command: string;
}

export interface CodeExecutionRequest {
  sourceCode: string;
  language: string;
  input: string;
  timeoutSeconds: number;
  problemSlug?: string;
}

export interface LeetCodeSubmissionRequest {
  sourceCode: string;
  language: string;
  problemSlug: string;
  waitForResult?: boolean;
}

export interface LeetCodeSubmissionResponse {
  success: boolean;
  submissionId: string;
  problemSlug: string;
  language: string;
  status: string;
  leetcodeSubmissionUrl: string;
  emotionCheckRequired?: boolean;
  judgeResult?: Record<string, unknown>;
  error?: string;
  details?: string;
}

export interface LeetCodeAuthConnectRequest {
  leetcodeSession: string;
  csrfToken: string;
}

export interface LeetCodeAuthStatusResponse {
  connected: boolean;
}

export interface CognitiveSprintQuestion {
  prompt: string;
  options: string[];
}

export interface CognitiveSprintStartResponse {
  sprintId: string;
  system1TimeLimitSeconds: number;
  roundA: CognitiveSprintQuestion;
  roundB: CognitiveSprintQuestion;
}

export interface CognitiveSprintRoundAResponse {
  sprintId: string;
  round: 'A';
  withinTimeLimit: boolean;
  timeTakenMs: number;
  correct: boolean;
  nextRound: 'B';
}

export interface CognitiveSprintResultResponse {
  sprintId: string;
  roundA: { correct: boolean };
  roundB: { correct: boolean; timeTakenMs: number };
  thinkingStyle: string;
}

export interface RishiSessionStartRequest {
  language?: string;
  problemSlug?: string;
}

export interface RishiSessionStartResponse {
  sessionId: number;
  startedAt: string;
}

export interface RishiCodeChangeEvent {
  timestamp: string;
  editorVersion: number;
  rangeOffset: number;
  rangeLength: number;
  insertedChars: number;
  deletedChars: number;
  resultingCodeLength: number;
  activityState?: string;
  editorFocused?: boolean;
  windowFocused?: boolean;
  documentVisible?: boolean;
}

export interface RishiCodeChangeBatchRequest {
  events: RishiCodeChangeEvent[];
}

export interface RishiCompileAttemptRequest {
  success: boolean;
  executionTimeMs?: number;
  language?: string;
  problemSlug?: string;
  source?: string;
  errorMessage?: string;
  outputPreview?: string;
  submissionStatus?: string;
  judgeMessage?: string;
  testsPassed?: number;
  testsTotal?: number;
  failedTestInput?: string;
  expectedOutput?: string;
  actualOutput?: string;
  stackTraceSnippet?: string;
}

export interface RishiCompileAttemptAnalysisResponse {
  status: string;
  success: boolean;
  source: string;
  failureBucket: string;
  mistakeCategory: string;
  accuracyPct: number;
  summary: string;
  nextSteps: string[];
  recordedAt: string;
}

export interface RishiSessionEndRequest {
  reason?: string;
  activeDurationMs?: number;
  typingDurationMs?: number;
  cursorIdleDurationMs?: number;
  editorUnfocusedDurationMs?: number;
  tabHiddenDurationMs?: number;
}

export interface RishiGrowthMetrics {
  sessions: number;
  totalCodingMinutes: number;
  compileAttempts: number;
  compileSuccessRate: number;
  averageFirstSuccessSeconds: number;
  averageEditsPerSession: number;
}

export interface RishiGrowthSummaryResponse {
  days: number;
  current: RishiGrowthMetrics;
  previous: RishiGrowthMetrics;
  codingMinutesGrowthPct: number;
  successRateGrowthPct: number;
  firstSuccessSpeedGrowthPct: number;
  consistencyGrowthPct: number;
}

@Injectable({
  providedIn: 'root'
})
export class CompilerService {
  private http = inject(HttpClient);
  private apiUrl = '/api/compiler';
  private cognitiveApiUrl = '/api/cognitive';
  private rishiCodingApiUrl = '/api/rishi/coding';

  executeCode(request: CodeExecutionRequest): Observable<CompilationResult> {
    return this.http.post<CompilationResult>(`${this.apiUrl}/execute`, request);
  }

  getAvailableLanguages(): Observable<CompilerInfo[]> {
    return this.http.get<CompilerInfo[]>(`${this.apiUrl}/languages`);
  }

  submitToLeetCode(request: LeetCodeSubmissionRequest): Observable<LeetCodeSubmissionResponse> {
    return this.http.post<LeetCodeSubmissionResponse>(`${this.apiUrl}/leetcode/submit`, request);
  }

  getLeetCodeAuthStatus(): Observable<LeetCodeAuthStatusResponse> {
    return this.http.get<LeetCodeAuthStatusResponse>(`${this.apiUrl}/leetcode/auth-status`);
  }

  connectLeetCodeAuth(request: LeetCodeAuthConnectRequest): Observable<LeetCodeAuthStatusResponse> {
    return this.http.post<LeetCodeAuthStatusResponse>(`${this.apiUrl}/leetcode/connect`, request);
  }

  disconnectLeetCodeAuth(): Observable<LeetCodeAuthStatusResponse> {
    return this.http.delete<LeetCodeAuthStatusResponse>(`${this.apiUrl}/leetcode/connect`);
  }

  getLeetCodeQuestionDetails(slug: string, title?: string, url?: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/leetcode/question-details`, { slug, title, url });
  }

  startCognitiveSprint(): Observable<CognitiveSprintStartResponse> {
    return this.http.post<CognitiveSprintStartResponse>(`${this.cognitiveApiUrl}/sprint`, {
      action: 'start'
    });
  }

  submitCognitiveSprintRoundA(sprintId: string, selectedIndex: number): Observable<CognitiveSprintRoundAResponse> {
    return this.http.post<CognitiveSprintRoundAResponse>(`${this.cognitiveApiUrl}/sprint`, {
      action: 'submit_round_a',
      sprintId,
      selectedIndex
    });
  }

  submitCognitiveSprintRoundB(sprintId: string, selectedIndex: number): Observable<CognitiveSprintResultResponse> {
    return this.http.post<CognitiveSprintResultResponse>(`${this.cognitiveApiUrl}/sprint`, {
      action: 'submit_round_b',
      sprintId,
      selectedIndex
    });
  }

  recordFailureEmotion(emotion: 'frustrated' | 'neutral' | 'motivated'): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.cognitiveApiUrl}/emotion`, { emotion });
  }

  startRishiCodingSession(request: RishiSessionStartRequest): Observable<RishiSessionStartResponse> {
    return this.http.post<RishiSessionStartResponse>(`${this.rishiCodingApiUrl}/sessions/start`, request);
  }

  recordRishiCodeChanges(sessionId: number, request: RishiCodeChangeBatchRequest): Observable<{ acceptedEvents: number }> {
    return this.http.post<{ acceptedEvents: number }>(`${this.rishiCodingApiUrl}/sessions/${sessionId}/events`, request);
  }

  recordRishiCompileAttempt(sessionId: number, request: RishiCompileAttemptRequest): Observable<RishiCompileAttemptAnalysisResponse> {
    return this.http.post<RishiCompileAttemptAnalysisResponse>(`${this.rishiCodingApiUrl}/sessions/${sessionId}/compile`, request);
  }

  endRishiCodingSession(sessionId: number, request: RishiSessionEndRequest): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.rishiCodingApiUrl}/sessions/${sessionId}/end`, request);
  }

  getRishiGrowthSummary(days = 14): Observable<RishiGrowthSummaryResponse> {
    return this.http.get<RishiGrowthSummaryResponse>(`${this.rishiCodingApiUrl}/growth-summary?days=${days}`);
  }
}
