import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InterviewerFeedbackRequest } from '../../core/hr/hr.service';

@Component({
  selector: 'app-interview-rubric-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form class="noir-card p-5 space-y-4" (ngSubmit)="submit()">
      <h3 class="text-sm font-black uppercase text-noir-100">Interview Rubric</h3>

      <div class="grid md:grid-cols-2 gap-3">
        <label class="block text-xs text-noir-300">
          Technical (0-100)
          <input class="w-full mt-1 bg-noir-900 border border-noir-700 p-2" type="number" min="0" max="100"
            [(ngModel)]="model.technicalDepthScore" name="technicalDepthScore" />
        </label>
        <label class="block text-xs text-noir-300">
          Problem Solving (0-100)
          <input class="w-full mt-1 bg-noir-900 border border-noir-700 p-2" type="number" min="0" max="100"
            [(ngModel)]="model.problemSolvingScore" name="problemSolvingScore" />
        </label>
        <label class="block text-xs text-noir-300">
          Communication (0-100)
          <input class="w-full mt-1 bg-noir-900 border border-noir-700 p-2" type="number" min="0" max="100"
            [(ngModel)]="model.communicationScore" name="communicationScore" />
        </label>
        <label class="block text-xs text-noir-300">
          Consistency (0-100)
          <input class="w-full mt-1 bg-noir-900 border border-noir-700 p-2" type="number" min="0" max="100"
            [(ngModel)]="model.consistencyScore" name="consistencyScore" />
        </label>
        <label class="block text-xs text-noir-300">
          Growth (0-100)
          <input class="w-full mt-1 bg-noir-900 border border-noir-700 p-2" type="number" min="0" max="100"
            [(ngModel)]="model.growthScore" name="growthScore" />
        </label>
        <label class="block text-xs text-noir-300">
          Recommendation
          <select class="w-full mt-1 bg-noir-900 border border-noir-700 p-2"
            [(ngModel)]="model.recommendation" name="recommendation">
            <option value="Hire">Hire</option>
            <option value="Hold">Hold</option>
            <option value="Reject">Reject</option>
          </select>
        </label>
      </div>

      <label class="block text-xs text-noir-300">
        Recommendation reason
        <textarea class="w-full mt-1 bg-noir-900 border border-noir-700 p-2 h-20"
          [(ngModel)]="model.recommendationReason" name="recommendationReason"></textarea>
      </label>

      <label class="block text-xs text-noir-300">
        Notes
        <textarea class="w-full mt-1 bg-noir-900 border border-noir-700 p-2 h-28"
          [(ngModel)]="model.notes" name="notes"></textarea>
      </label>

      <button type="submit" [disabled]="saving"
        class="px-4 py-2 bg-crimson-600 text-white font-bold uppercase text-xs border border-crimson-500 disabled:opacity-50">
        {{ saving ? 'Submitting...' : 'Submit Feedback' }}
      </button>
    </form>
  `
})
export class InterviewRubricFormComponent {
  @Input() saving = false;
  @Output() save = new EventEmitter<InterviewerFeedbackRequest>();

  model: InterviewerFeedbackRequest = {
    technicalDepthScore: 60,
    problemSolvingScore: 60,
    communicationScore: 60,
    consistencyScore: 60,
    growthScore: 60,
    recommendation: 'Hold',
    recommendationReason: '',
    notes: ''
  };

  submit(): void {
    this.save.emit({ ...this.model });
  }
}
