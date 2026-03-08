import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface HrCandidateCard {
  candidateId: number;
  name: string;
  email: string;
  leetcodeUsername: string;
  overallReadinessScore: number;
  technicalScore: number;
  communicationScore: number;
  consistencyScore: number;
  confidenceScore: number;
  trend: string;
  recommendationBand: string;
  lastActiveAt: string;
  riskFlags: string[];
  positiveSignals: string[];
}

export interface HrCandidateSummary {
  candidateId: number;
  name: string;
  email: string;
  leetcodeUsername: string;
  overallReadinessScore: number;
  technicalScore: number;
  processScore: number;
  communicationScore: number;
  consistencyScore: number;
  growthScore: number;
  confidenceScore: number;
  trend: string;
  recommendationBand: string;
  aiBriefing: string;
  lastActiveAt: string;
  radar: Record<string, number>;
  heatmap: Record<string, number>;
  timeline: Record<string, number>;
  riskFlags: string[];
  positiveSignals: string[];
  recentFeedback: Array<Record<string, any>>;
}

export interface HrInterviewInsights {
  candidateId: number;
  briefing: string;
  behavioralScores: Record<string, number>;
  recommendedFocusAreas: string[];
  suggestedQuestions: string[];
}

export interface InterviewerFeedbackRequest {
  technicalDepthScore: number;
  problemSolvingScore: number;
  communicationScore: number;
  consistencyScore: number;
  growthScore: number;
  recommendation: string;
  recommendationReason: string;
  notes: string;
}

@Injectable({
  providedIn: 'root'
})
export class HrService {
  private http = inject(HttpClient);

  getCandidates(name?: string): Observable<HrCandidateCard[]> {
    let params = new HttpParams();
    if (name && name.trim()) {
      params = params.set('name', name.trim());
    }
    return this.http.get<HrCandidateCard[]>('/api/hr/candidates', { params });
  }

  getCandidateSummary(candidateId: number): Observable<HrCandidateSummary> {
    return this.http.get<HrCandidateSummary>(`/api/hr/candidates/${candidateId}/summary`);
  }

  getInterviewInsights(candidateId: number): Observable<HrInterviewInsights> {
    return this.http.get<HrInterviewInsights>(`/api/hr/candidates/${candidateId}/interview-insights`);
  }

  submitFeedback(candidateId: number, payload: InterviewerFeedbackRequest): Observable<any> {
    return this.http.post(`/api/interviewer/candidates/${candidateId}/feedback`, payload);
  }
}
