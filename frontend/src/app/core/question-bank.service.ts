import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface LeetCodeQuestion {
  title: string;
  url: string;
  difficulty: string;
  tags: string[];
  slug?: string;
}

@Injectable({
  providedIn: 'root'
})
export class QuestionBankService {
  private http = inject(HttpClient);
  private apiUrl = '/api/questions';

  getCommonQuestions(): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/common`);
  }

  getTopTierQuestions(): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/top-tier`);
  }

  getTrendingQuestions(): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/trending`);
  }

  getAllQuestions(): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/all`);
  }

  getByDifficulty(difficulty: string): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/by-difficulty`, {
      params: { difficulty }
    });
  }

  getByTag(tag: string): Observable<LeetCodeQuestion[]> {
    return this.http.get<LeetCodeQuestion[]>(`${this.apiUrl}/by-tag`, {
      params: { tag }
    });
  }

  getDailyChallenge(): Observable<LeetCodeQuestion> {
    return this.http.get<LeetCodeQuestion>(`${this.apiUrl}/daily-challenge`);
  }

  getSmartPick(): Observable<LeetCodeQuestion> {
    return this.http.get<LeetCodeQuestion>(`${this.apiUrl}/smart-pick`);
  }
}
