import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HrCandidateCard, HrCandidateSummary, HrService } from '../core/hr/hr.service';
import { CandidateSummaryCardComponent } from '../hr/shared/candidate-summary-card.component';
import { RiskFlagsPanelComponent } from '../hr/shared/risk-flags-panel.component';
import { SkillRadarComponent } from '../hr/shared/skill-radar.component';

@Component({
  selector: 'app-hr-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    CandidateSummaryCardComponent,
    RiskFlagsPanelComponent,
    SkillRadarComponent
  ],
  templateUrl: './hr-dashboard.component.html',
  styleUrl: './hr-dashboard.component.css'
})
export class HrDashboardComponent {
  private hrService = inject(HrService);

  search = '';
  loading = signal(false);
  summaryLoading = signal(false);
  candidates = signal<HrCandidateCard[]>([]);
  selectedSummary = signal<HrCandidateSummary | null>(null);
  error = signal<string | null>(null);

  constructor() {
    this.loadCandidates();
  }

  loadCandidates(): void {
    this.loading.set(true);
    this.error.set(null);
    this.hrService.getCandidates(this.search).subscribe({
      next: (rows) => {
        this.candidates.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load candidates.');
        this.loading.set(false);
      }
    });
  }

  openCandidate(candidateId: number): void {
    this.summaryLoading.set(true);
    this.hrService.getCandidateSummary(candidateId).subscribe({
      next: (summary) => {
        this.selectedSummary.set(summary);
        this.summaryLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load candidate summary.');
        this.summaryLoading.set(false);
      }
    });
  }
}
