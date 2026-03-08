import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-risk-flags-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="grid md:grid-cols-2 gap-4">
      <div class="noir-card p-5 border-l-2 border-l-red-500">
        <h3 class="text-sm font-black uppercase mb-3 text-red-400">Risk Flags</h3>
        <ul *ngIf="riskFlags?.length; else noRisk" class="space-y-2 text-sm text-noir-200">
          <li *ngFor="let flag of riskFlags">- {{ flag }}</li>
        </ul>
        <ng-template #noRisk>
          <p class="text-sm text-noir-400">No major risk flags detected in current data.</p>
        </ng-template>
      </div>

      <div class="noir-card p-5 border-l-2 border-l-emerald-500">
        <h3 class="text-sm font-black uppercase mb-3 text-emerald-400">Positive Signals</h3>
        <ul *ngIf="positiveSignals?.length; else noSignals" class="space-y-2 text-sm text-noir-200">
          <li *ngFor="let signal of positiveSignals">- {{ signal }}</li>
        </ul>
        <ng-template #noSignals>
          <p class="text-sm text-noir-400">No strong positive trend yet.</p>
        </ng-template>
      </div>
    </section>
  `
})
export class RiskFlagsPanelComponent {
  @Input() riskFlags: string[] = [];
  @Input() positiveSignals: string[] = [];
}
