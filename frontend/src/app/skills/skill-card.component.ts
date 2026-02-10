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
      class="noir-card p-10 group cursor-pointer transition-all duration-500 hover:bg-noir-900 animate-reveal"
      (click)="updateRating.emit(skill.rating)"
    >
      <!-- Industrial Header -->
      <div class="flex items-start justify-between mb-8">
        <div class="p-3 bg-noir-800 border border-noir-700 text-noir-400 group-hover:text-crimson-500 group-hover:border-crimson-500 transition-all duration-500 rounded-none">
          <lucide-icon [name]="skill.icon || 'Zap'" class="w-6 h-6"></lucide-icon>
        </div>
        <div class="text-mono-technical group-hover:text-crimson-500 transition-colors">
          {{ skill.category }}
        </div>
      </div>

      <!-- Aggressive Typography -->
      <h3 class="text-2xl font-black uppercase text-noir-100 mb-6 group-hover:text-white transition-colors leading-none tracking-tighter">
        {{ skill.name }}
      </h3>

      <!-- Industrial Rating Bar -->
      <div class="space-y-3">
        <div class="flex justify-between text-mono-technical opacity-50 group-hover:opacity-100 transition-opacity">
          <span>Proficiency</span>
          <span>{{ skill.rating }}/05</span>
        </div>
        <div class="flex gap-2">
          @for (star of [1,2,3,4,5]; track star) {
            <div 
              class="flex-1 h-3 transition-all duration-700"
              [class.bg-crimson-600]="star <= skill.rating"
              [class.shadow-[0_0_15px_rgba(239,68,68,0.4)]]="star <= skill.rating"
              [class.bg-noir-800]="star > skill.rating"
              (click)="updateRating.emit(star); $event.stopPropagation()"
            ></div>
          }
        </div>
      </div>
      
      <!-- Scanline Overlay on Hover -->
      <div class="absolute inset-0 scanline opacity-0 group-hover:opacity-10 transition-opacity pointer-events-none"></div>
    </div>
  `
})
export class SkillCardComponent {
  @Input({ required: true }) skill!: Skill;
  @Output() updateRating = new EventEmitter<number>();
}
