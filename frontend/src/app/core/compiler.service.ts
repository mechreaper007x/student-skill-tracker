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

@Injectable({
  providedIn: 'root'
})
export class CompilerService {
  private http = inject(HttpClient);
  private apiUrl = '/api/compiler';

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

  getLeetCodeQuestionDetails(slug: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/leetcode/question-details`, { slug });
  }
}
