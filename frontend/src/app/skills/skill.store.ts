import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { computed, inject, PLATFORM_ID } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';

export type SkillCategory = 'Technical' | 'Humanistic' | 'Arcane';

export interface Skill {
  id: string;
  name: string;
  category: SkillCategory;
  rating: number; // 1-5
  maxRating: number;
  icon?: string;
  description?: string;
  problemsSolved?: number; // For language skills from LeetCode
}

export interface SkillState {
  skills: Skill[];
  loading: boolean;
  filter: SkillCategory | 'All';
}

const initialState: SkillState = {
  skills: [],
  loading: false,
  filter: 'All'
};

export const SkillStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, http = inject(HttpClient)) => ({
    loadLanguageSkills() {
      patchState(store, { loading: true });
      http.get<Skill[]>('/api/students/me/language-skills').subscribe({
        next: (skills) => patchState(store, { skills: skills || [], loading: false }),
        error: (error) => {
          console.error('Failed to load language skills:', error);
          patchState(store, { skills: [], loading: false });
        }
      });
    },
    updateRating(id: string, newRating: number) {
      patchState(store, (state) => ({
        skills: state.skills.map(skill => 
          skill.id === id ? { ...skill, rating: newRating } : skill
        )
      }));
    },
    setFilter(filter: SkillCategory | 'All') {
      patchState(store, { filter });
    }
  })),
  withComputed((store) => ({
    filteredSkills: computed(() => {
      const filter = store.filter();
      if (filter === 'All') return store.skills();
      return store.skills().filter(skill => skill.category === filter);
    })
  })),
  withHooks({
    onInit(store) {
      const platformId = inject(PLATFORM_ID);
      if (isPlatformBrowser(platformId)) {
        store.loadLanguageSkills();
      }
    }
  })
);
