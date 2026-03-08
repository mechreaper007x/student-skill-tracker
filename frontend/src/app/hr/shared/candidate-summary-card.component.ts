import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { HrCandidateSummary } from '../../core/hr/hr.service';

@Component({
  selector: 'app-candidate-summary-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section *ngIf="summary" class="noir-card p-6 border-l-2 border-l-crimson-500 space-y-4">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h2 class="text-2xl font-black uppercase text-white">{{ summary.name }}</h2>
          <p class="text-xs text-noir-400">{{ summary.email }} | {{ summary.leetcodeUsername }}</p>
        </div>
        <span class="text-xs font-bold uppercase px-3 py-1 border border-noir-700 text-crimson-400">
          {{ summary.recommendationBand }}
        </span>
      </div>

      <p class="text-sm text-noir-300">{{ summary.aiBriefing }}</p>

      <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div class="bg-noir-900 p-3 border border-noir-800">
          <div class="text-[10px] uppercase text-noir-500">Overall</div>
          <div class="text-lg font-bold text-white">{{ summary.overallReadinessScore | number: '1.0-1' }}</div>
        </div>
        <div class="bg-noir-900 p-3 border border-noir-800">
          <div class="text-[10px] uppercase text-noir-500">Technical</div>
          <div class="text-lg font-bold text-white">{{ summary.technicalScore | number: '1.0-1' }}</div>
        </div>
        <div class="bg-noir-900 p-3 border border-noir-800">
          <div class="text-[10px] uppercase text-noir-500">Communication</div>
          <div class="text-lg font-bold text-white">{{ summary.communicationScore | number: '1.0-1' }}</div>
        </div>
        <div class="bg-noir-900 p-3 border border-noir-800">
          <div class="text-[10px] uppercase text-noir-500">Consistency</div>
          <div class="text-lg font-bold text-white">{{ summary.consistencyScore | number: '1.0-1' }}</div>
        </div>
        <div class="bg-noir-900 p-3 border border-noir-800">
          <div class="text-[10px] uppercase text-noir-500">Confidence</div>
          <div class="text-lg font-bold text-white">{{ summary.confidenceScore | number: '1.0-1' }}</div>
        </div>
      </div>
    </section>
  `
})
export class CandidateSummaryCardComponent {
  @Input() summary: HrCandidateSummary | null = null;
}
