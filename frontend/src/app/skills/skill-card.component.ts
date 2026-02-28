import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { Skill } from './skill.store';

@Component({
  selector: 'app-skill-card',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div 
      class="noir-card p-10 group cursor-pointer transition-all duration-500 hover:bg-noir-900 animate-reveal border-l-2"
      [style.border-left-color]="skill.color || '#ef4444'"
      (click)="onCardClick()"
    >
      <!-- Industrial Header -->
      <div class="flex items-start justify-between mb-8">
        <div 
            class="p-3 bg-noir-800 border transition-all duration-500 rounded-none shadow-lg"
            [style.color]="skill.color || '#ef4444'"
            [style.border-color]="skill.color || '#ef4444'"
            [style.box-shadow]="'0 0 20px ' + (skill.color || '#ef4444') + '33'"
        >
          <lucide-icon [name]="skill.icon || 'Zap'" class="w-6 h-6"></lucide-icon>
        </div>
        <div class="text-[10px] font-mono uppercase tracking-[0.2em] opacity-40 group-hover:opacity-100 transition-opacity" [style.color]="skill.color">
          NEURAL_LINK // {{ skill.category }}
        </div>
      </div>

      <!-- Aggressive Typography -->
      <h3 class="text-2xl font-black uppercase text-noir-100 mb-2 group-hover:text-white transition-colors leading-none tracking-tighter">
        {{ skill.name }}
      </h3>
      <p class="text-[10px] font-mono text-noir-500 mb-6 uppercase tracking-wider">{{ skill.description }}</p>

      <!-- Psychological Insight -->
      <div *ngIf="skill.insight" class="mb-8 p-4 bg-black/40 border border-noir-800/50 relative overflow-hidden group-hover:border-noir-700 transition-colors">
        <div class="absolute top-0 left-0 w-1 h-full" [style.background-color]="skill.color"></div>
        <p class="text-[11px] leading-relaxed text-noir-300 italic">
          <span class="text-noir-500 not-italic mr-1">RISHI'S_INSIGHT:</span>
          {{ skill.insight }}
        </p>
      </div>

      <!-- Industrial Rating Bar -->
      <div class="space-y-4">
        <div class="flex justify-between items-end">
            <span class="text-[10px] font-mono uppercase tracking-widest text-noir-500">Cognitive_Depth</span>
            <span class="text-xl font-black italic text-white" [style.color]="skill.color">
                {{ skill.rating }}<span class="text-[10px] opacity-30 italic">/05</span>
            </span>
        </div>
        <div class="flex gap-1.5">
          @for (star of [1,2,3,4,5]; track star) {
            <div 
              class="flex-1 h-1.5 transition-all duration-700 relative overflow-hidden"
              [class.bg-noir-800]="star > skill.rating"
              (click)="updateRating.emit(star); $event.stopPropagation()"
            >
                <div 
                    *ngIf="star <= skill.rating"
                    class="absolute inset-0 transition-all duration-500"
                    [style.background-color]="skill.color || '#ef4444'"
                    [style.box-shadow]="'0 0 15px ' + (skill.color || '#ef4444') + 'aa'"
                ></div>
            </div>
          }
        </div>
      </div>

      <button
        *ngIf="skill.testTrack"
        (click)="openTests.emit(); $event.stopPropagation()"
        class="mt-6 w-full px-4 py-2 text-[10px] font-black uppercase tracking-widest border border-noir-700 text-noir-300 hover:text-white hover:border-indigo-500 transition-all"
      >
        Open Tests
      </button>
      
      <!-- Scanline Overlay on Hover -->
      <div class="absolute inset-0 scanline opacity-0 group-hover:opacity-5 transition-opacity pointer-events-none"></div>
    </div>
  `
})
export class SkillCardComponent {
  @Input({ required: true }) skill!: Skill;
  @Output() updateRating = new EventEmitter<number>();
  @Output() openTests = new EventEmitter<void>();

  onCardClick() {
    if (this.skill.testTrack) {
      this.openTests.emit();
      return;
    }
    this.updateRating.emit(this.skill.rating);
  }
}
