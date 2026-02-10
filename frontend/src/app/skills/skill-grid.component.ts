import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { SkillCardComponent } from './skill-card.component';
import { SkillCategory, SkillStore } from './skill.store';

@Component({
  selector: 'app-skill-grid',
  standalone: true,
  imports: [CommonModule, SkillCardComponent, LucideAngularModule],
  template: `
    <div class="p-8 space-y-8 animate-fade-in">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div>
          <h1 class="text-4xl font-bold tracking-tighter text-white mb-2 uppercase italic">Will to Power</h1>
          <p class="text-noir-400 font-mono text-sm tracking-wide">Master your technical and philosophical arsenal.</p>
        </div>
        
        <!-- Filter Controls -->
        <div class="flex items-center bg-noir-900 p-1 rounded-xl border border-noir-800">
          <button 
            (click)="setFilter('All')"
            class="px-5 py-2 text-xs font-bold uppercase tracking-widest rounded-lg transition-all"
            [class.bg-noir-800]="store.filter() === 'All'"
            [class.text-white]="store.filter() === 'All'"
            [class.text-noir-500]="store.filter() !== 'All'"
          >All</button>
          <button 
            (click)="setFilter('Technical')"
            class="px-5 py-2 text-xs font-bold uppercase tracking-widest rounded-lg transition-all"
            [class.bg-crimson-600]="store.filter() === 'Technical'"
            [class.text-white]="store.filter() === 'Technical'"
            [class.text-noir-500]="store.filter() !== 'Technical'"
          >Technical</button>
          <button 
            (click)="setFilter('Humanistic')"
            class="px-5 py-2 text-xs font-bold uppercase tracking-widest rounded-lg transition-all"
            [class.bg-noir-100]="store.filter() === 'Humanistic'"
            [class.text-noir-950]="store.filter() === 'Humanistic'"
            [class.text-noir-500]="store.filter() !== 'Humanistic'"
          >Humanistic</button>
        </div>
      </div>

      <!-- Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        @for (skill of filteredSkills(); track skill.id) {
          <app-skill-card 
            [skill]="skill"
            (updateRating)="onUpdateRating(skill.id, $event)"
          ></app-skill-card>
        }
      </div>
    </div>
  `
})
export class SkillGridComponent {
  readonly store = inject(SkillStore);
  
  filteredSkills = this.store.filteredSkills;

  setFilter(filter: SkillCategory | 'All') {
    this.store.setFilter(filter);
  }

  onUpdateRating(id: string, rating: number) {
    this.store.updateRating(id, rating);
  }
}
