import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { SkillCardComponent } from './skill-card.component';
import { Skill } from './skill.store';

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
          <p class="text-noir-400 font-mono text-sm tracking-wide">Humanistic mastery from your coding behavior: problem-solving, reasoning, critical thinking, and EQ.</p>
        </div>
        
        <!-- Filter Controls -->
        <div class="flex items-center bg-noir-900 p-1 rounded-xl border border-noir-800">
          <button 
            (click)="setFilter('All')"
            class="px-5 py-2 text-xs font-bold uppercase tracking-widest rounded-lg transition-all"
            [class.bg-noir-800]="filter() === 'All'"
            [class.text-white]="filter() === 'All'"
            [class.text-noir-500]="filter() !== 'All'"
          >All</button>
          <button 
            (click)="setFilter('Humanistic')"
            class="px-5 py-2 text-xs font-bold uppercase tracking-widest rounded-lg transition-all"
            [class.bg-crimson-600]="filter() === 'Humanistic'"
            [class.text-white]="filter() === 'Humanistic'"
            [class.text-noir-500]="filter() !== 'Humanistic'"
          >Humanistic</button>
        </div>
      </div>

      @if (loading()) {
        <div class="noir-card p-6 text-xs font-mono text-noir-400 animate-pulse">Calibrating humanistic profile...</div>
      }

      @if (error()) {
        <div class="noir-card p-6 border border-amber-500/30 bg-amber-950/20 text-xs font-mono text-amber-300">{{ error() }}</div>
      }

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
export class SkillGridComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  skills = signal<Skill[]>([]);
  filter = signal<'All' | 'Humanistic'>('All');
  loading = signal(false);
  error = signal<string | null>(null);

  filteredSkills = computed(() => {
    if (this.filter() === 'All') {
      return this.skills();
    }
    return this.skills().filter((skill) => skill.category === 'Humanistic');
  });

  ngOnInit() {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.loadHumanisticSkills();
  }

  setFilter(filter: 'All' | 'Humanistic') {
    this.filter.set(filter);
  }

  onUpdateRating(id: string, rating: number) {
    this.skills.update((current) => current.map((skill) => {
      if (skill.id !== id) {
        return skill;
      }
      return { ...skill, rating };
    }));
  }

  private loadHumanisticSkills() {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<any>('/api/students/me/dashboard').subscribe({
      next: (dashboard) => {
        const mapped = this.mapHumanisticSkills(dashboard?.skillData ?? {});
        this.skills.set(mapped);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load humanistic skills for Will to Power', err);
        this.skills.set(this.mapHumanisticSkills({}));
        this.error.set('Humanistic profile is temporarily unavailable. Showing baseline metrics.');
        this.loading.set(false);
      }
    });
  }

  private mapHumanisticSkills(skillData: any): Skill[] {
    const problemSolvingScore = this.asNumber(skillData?.problemSolvingScore);
    const algorithmsScore = this.asNumber(skillData?.algorithmsScore);
    const dataStructuresScore = this.asNumber(skillData?.dataStructuresScore);
    const easy = this.asNumber(skillData?.easyProblems);
    const medium = this.asNumber(skillData?.mediumProblems);
    const hard = this.asNumber(skillData?.hardProblems);
    const total = this.asNumber(skillData?.totalProblemsSolved);
    const solved = total > 0 ? total : (easy + medium + hard);

    const solvedSafe = solved > 0 ? solved : 1;
    const mediumHardRatio = (medium + hard) / solvedSafe;
    const hardRatio = hard / solvedSafe;
    const breadth = ([easy, medium, hard].filter((value) => value > 0).length / 3) * 100;
    const persistence = Math.min(100, (solved / 200) * 100);

    const reasoningScore = this.clamp01To100((0.75 * algorithmsScore) + (25 * mediumHardRatio));
    const criticalThinkingScore = this.clamp01To100((0.65 * dataStructuresScore) + (35 * hardRatio));
    const eqScore = this.clamp01To100((0.55 * breadth) + (0.45 * persistence));

    return [
      {
        id: 'human-problem-solving',
        name: 'Problem-Solving',
        category: 'Humanistic',
        rating: this.scoreToRating(problemSolvingScore),
        maxRating: 5,
        icon: 'Puzzle',
        description: `Direct skill score from your LeetCode profile dynamics. Solved: ${solved}.`
      },
      {
        id: 'human-reasoning-ability',
        name: 'Reasoning Ability',
        category: 'Humanistic',
        rating: this.scoreToRating(reasoningScore),
        maxRating: 5,
        icon: 'Brain',
        description: `Weighted by algorithm depth and medium/hard exposure (${medium + hard}/${solvedSafe}).`
      },
      {
        id: 'human-critical-thinking',
        name: 'Critical Thinking',
        category: 'Humanistic',
        rating: this.scoreToRating(criticalThinkingScore),
        maxRating: 5,
        icon: 'Target',
        description: `Derived from structural complexity and hard-problem ratio (${hard}/${solvedSafe}).`
      },
      {
        id: 'human-eq',
        name: 'EQ',
        category: 'Humanistic',
        rating: this.scoreToRating(eqScore),
        maxRating: 5,
        icon: 'Heart',
        description: 'Learning EQ proxy based on consistency breadth and sustained problem-solving effort.'
      }
    ];
  }

  private scoreToRating(score: number): number {
    if (!Number.isFinite(score) || score <= 0) {
      return 1;
    }
    return Math.max(1, Math.min(5, Math.ceil(score / 20)));
  }

  private asNumber(value: unknown): number {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
    if (value === null || value === undefined) {
      return 0;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private clamp01To100(value: number): number {
    if (!Number.isFinite(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, value));
  }
}
