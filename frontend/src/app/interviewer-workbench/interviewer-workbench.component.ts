import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import {
  HrCandidateSummary,
  HrInterviewInsights,
  HrService,
  InterviewerFeedbackRequest
} from '../core/hr/hr.service';
import { CandidateSummaryCardComponent } from '../hr/shared/candidate-summary-card.component';
import { InterviewRubricFormComponent } from '../hr/shared/interview-rubric-form.component';
import { RiskFlagsPanelComponent } from '../hr/shared/risk-flags-panel.component';

@Component({
  selector: 'app-interviewer-workbench',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    CandidateSummaryCardComponent,
    InterviewRubricFormComponent,
    RiskFlagsPanelComponent
  ],
  templateUrl: './interviewer-workbench.component.html',
  styleUrl: './interviewer-workbench.component.css'
})
export class InterviewerWorkbenchComponent {
  private route = inject(ActivatedRoute);
  private hrService = inject(HrService);

  candidateId = Number(this.route.snapshot.paramMap.get('candidateId'));
  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);

  summary = signal<HrCandidateSummary | null>(null);
  insights = signal<HrInterviewInsights | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    if (!this.candidateId || Number.isNaN(this.candidateId)) {
      this.error.set('Invalid candidate id.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.hrService.getCandidateSummary(this.candidateId).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.hrService.getInterviewInsights(this.candidateId).subscribe({
          next: (insights) => {
            this.insights.set(insights);
            this.loading.set(false);
          },
          error: () => {
            this.error.set('Failed to load interview insights.');
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.error.set('Failed to load candidate summary.');
        this.loading.set(false);
      }
    });
  }

  submitFeedback(payload: InterviewerFeedbackRequest): void {
    this.saving.set(true);
    this.success.set(null);
    this.error.set(null);

    this.hrService.submitFeedback(this.candidateId, payload).subscribe({
      next: () => {
        this.success.set('Feedback submitted successfully.');
        this.saving.set(false);
        this.load();
      },
      error: () => {
        this.error.set('Failed to submit feedback.');
        this.saving.set(false);
      }
    });
  }
}
