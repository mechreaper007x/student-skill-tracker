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

@Injectable({
  providedIn: 'root'
})
export class CompilerService {
  private http = inject(HttpClient);
  private apiUrl = '/api/compiler';
  private cognitiveApiUrl = '/api/cognitive';

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
}
