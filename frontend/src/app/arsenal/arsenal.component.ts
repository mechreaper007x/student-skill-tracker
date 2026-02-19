import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, of } from 'rxjs';

// --- Interfaces ---
interface Weapon {
  name: string;
  language: string;
  stars: number;
  forks: number;
  description: string;
  lastForged: string; // last commit date
  url: string;
}

interface Medal {
  title: string;
  description: string;
  icon: string;
  tier: 'bronze' | 'silver' | 'gold' | 'crimson';
  unlocked: boolean;
}

interface Technique {
  name: string;
  pattern: string;
  difficulty: 'Initiate' | 'Adept' | 'Master';
  description: string;
  url: string;
  tags: string[];
  solvedRecently: boolean;
  dominantLanguage: string;
  dominantLanguageSharePercent: number;
  recommendationScore: number;
}

interface LanguageSkill {
  id: string;
  name: string;
  rating: number;
  problemsSolved?: number;
}

interface CommonQuestion {
  title?: string;
  url?: string;
  difficulty?: string;
  tags?: string[];
  matchedTags?: string[];
  recommendationScore?: number;
  recommended?: boolean;
}

interface RecentAcSubmission {
  title?: string;
  titleSlug?: string;
  timestamp?: string;
  lang?: string;
  langName?: string;
}

interface AlgorithmMastery {
  tagName: string;
  tagSlug: string;
  level: string;
  problemsSolved: number;
  totalQuestions: number;
  masteryPercent: number;
  masteryBand: string;
}

@Component({
  selector: 'app-arsenal',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="flex min-h-screen animate-fade-in relative">
      
      <!-- DETAILED REPO OVERLAY (MODAL) -->
      @if (selectedRepo()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in" (click)="closeRepoDetail()">
          <div class="noir-card w-full max-w-4xl max-h-[90vh] overflow-y-auto border-crimson-500/50 shadow-[0_0_50px_rgba(220,20,60,0.2)] bg-noir-950 flex flex-col" (click)="$event.stopPropagation()">
            
            <!-- Modal Header -->
            <div class="p-6 border-b border-noir-800 flex justify-between items-start sticky top-0 bg-noir-950/95 backdrop-blur z-10">
              <div>
                <h2 class="text-3xl font-black uppercase tracking-tighter text-white mb-2">{{ selectedRepo()?.name }}</h2>
                <p class="text-noir-400 font-mono text-sm">{{ selectedRepo()?.description }}</p>
                <div class="flex gap-4 mt-4">
                  <a [href]="selectedRepo()?.url" target="_blank" class="flex items-center gap-2 text-crimson-400 hover:text-crimson-300 transition-colors font-mono text-xs uppercase tracking-widest">
                    <lucide-icon name="ExternalLink" class="w-4 h-4"></lucide-icon>
                    View on GitHub
                  </a>
                  <div class="flex items-center gap-4 text-xs font-mono text-noir-500">
                    <span class="flex items-center gap-1"><lucide-icon name="Star" class="w-3 h-3"></lucide-icon> {{ selectedRepo()?.stars }}</span>
                    <span class="flex items-center gap-1"><lucide-icon name="GitFork" class="w-3 h-3"></lucide-icon> {{ selectedRepo()?.forks }}</span>
                  </div>
                </div>
              </div>
              <button (click)="closeRepoDetail()" class="text-noir-500 hover:text-crimson-500 transition-colors">
                <lucide-icon name="X" class="w-8 h-8"></lucide-icon>
              </button>
            </div>

            <div class="p-6 grid grid-cols-1 md:grid-cols-2 gap-8">
              
              <!-- LEFT COLUMN: Language Proficiency -->
              <div>
                <h3 class="text-xl font-bold uppercase tracking-widest text-noir-200 mb-6 flex items-center gap-2">
                  <lucide-icon name="Code" class="w-5 h-5 text-crimson-500"></lucide-icon>
                  Tech Stack
                </h3>
                
                @if (isLoadingDetails()) {
                  <div class="flex items-center gap-2 text-noir-500 font-mono text-sm animate-pulse">
                    <lucide-icon name="Loader2" class="w-4 h-4 animate-spin"></lucide-icon>
                    Analyzing codebase...
                  </div>
                } @else {
                  <div class="space-y-4">
                     @for (lang of repoLanguages(); track lang.name) {
                       <div>
                         <div class="flex justify-between text-xs font-mono mb-1">
                           <span class="text-noir-300">{{ lang.name }}</span>
                           <span class="text-noir-500">{{ lang.percentage }}%</span>
                         </div>
                         <div class="h-1.5 w-full bg-noir-900 rounded-full overflow-hidden">
                            <div class="h-full bg-crimson-600 shadow-[0_0_10px_rgba(220,20,60,0.5)]" [style.width.%]="lang.percentage"></div>
                         </div>
                       </div>
                     }
                     @if (repoLanguages().length === 0) {
                        <p class="text-noir-600 font-mono text-sm italic">No language data detected.</p>
                     }
                  </div>
                }
              </div>

              <!-- RIGHT COLUMN: Skeleton (File Tree) -->
              <div>
                <h3 class="text-xl font-bold uppercase tracking-widest text-noir-200 mb-6 flex items-center gap-2">
                  <lucide-icon name="Network" class="w-5 h-5 text-crimson-500"></lucide-icon>
                  Skeleton
                </h3>

                <div class="bg-black/50 border border-noir-800 rounded p-4 font-mono text-xs text-noir-300 h-96 overflow-y-auto custom-scrollbar">
                  @if (isLoadingDetails()) {
                     <div class="space-y-2 animate-pulse">
                        <div class="h-3 bg-noir-900 w-3/4 rounded"></div>
                        <div class="h-3 bg-noir-900 w-1/2 rounded ml-4"></div>
                        <div class="h-3 bg-noir-900 w-2/3 rounded ml-4"></div>
                     </div>
                  } @else {
                    <ul class="space-y-1">
                      @for (node of repoSkeleton(); track node.path) {
                        <li class="flex items-start gap-2 hover:text-white transition-colors cursor-default">
                          @if (node.type === 'tree') {
                            <lucide-icon name="Folder" class="w-3 h-3 text-amber-600 mt-0.5 shrink-0"></lucide-icon>
                          } @else {
                            <lucide-icon name="FileCode" class="w-3 h-3 text-noir-600 mt-0.5 shrink-0"></lucide-icon>
                          }
                          <span class="break-all">{{ node.path }}</span>
                        </li>
                      }
                      @if (repoSkeleton().length === 0) {
                         <li class="text-noir-600 italic">Structure unavailable or empty.</li>
                      }
                    </ul>
                  }
                </div>
              </div>

            </div>

          </div>
        </div>
      }

      <!-- VERTICAL SIDE BANNER -->
      <div class="hidden md:flex flex-col justify-between items-center w-24 bg-noir-950 border-r border-noir-800 py-12 relative overflow-hidden">
        <!-- Vertical Text -->
        <div class="rotate-180 flex-1 flex flex-col items-center justify-center gap-8 writing-mode-vertical">
          <h1 class="text-6xl font-black uppercase tracking-tighter text-noir-200 opacity-20 whitespace-nowrap select-none" style="writing-mode: vertical-rl;">
            THE ARSENAL
          </h1>
        </div>
        <div class="z-10 bg-noir-900 p-3 rounded-full border border-noir-700">
           <lucide-icon name="Package" class="text-crimson-600 w-6 h-6"></lucide-icon>
        </div>
      </div>

      <!-- MAIN CONTENT -->
      <div class="flex-1 p-6 md:p-12 overflow-y-auto h-screen">
        
        <!-- MOBILE HEADER (Visible only on small screens) -->
        <div class="md:hidden flex items-center justify-between mb-8">
           <h1 class="text-3xl font-black uppercase tracking-tighter text-white">The Arsenal</h1>
           <lucide-icon name="Package" class="text-crimson-600 w-8 h-8"></lucide-icon>
        </div>

        <!-- TABS -->
        <div class="flex items-center gap-8 border-b border-noir-800 mb-12">
          <button 
            (click)="activeTab.set('LEETCODE')"
            class="pb-4 text-xl font-bold tracking-tighter uppercase transition-colors relative"
            [class.text-crimson-500]="activeTab() === 'LEETCODE'"
            [class.text-noir-500]="activeTab() !== 'LEETCODE'"
          >
            LeetCode
            <div *ngIf="activeTab() === 'LEETCODE'" class="absolute bottom-0 left-0 w-full h-0.5 bg-crimson-500 shadow-[0_0_10px_rgba(239,68,68,0.5)]"></div>
          </button>

          <button 
            (click)="activeTab.set('GITHUB')"
            class="pb-4 text-xl font-bold tracking-tighter uppercase transition-colors relative"
            [class.text-crimson-500]="activeTab() === 'GITHUB'"
            [class.text-noir-500]="activeTab() !== 'GITHUB'"
          >
            GitHub
            <div *ngIf="activeTab() === 'GITHUB'" class="absolute bottom-0 left-0 w-full h-0.5 bg-crimson-500 shadow-[0_0_10px_rgba(239,68,68,0.5)]"></div>
          </button>

          <button 
            (click)="activeTab.set('CODEX')"
            class="pb-4 text-xl font-bold tracking-tighter uppercase transition-colors relative"
            [class.text-crimson-500]="activeTab() === 'CODEX'"
            [class.text-noir-500]="activeTab() !== 'CODEX'"
          >
            The Codex
            <div *ngIf="activeTab() === 'CODEX'" class="absolute bottom-0 left-0 w-full h-0.5 bg-crimson-500 shadow-[0_0_10px_rgba(239,68,68,0.5)]"></div>
          </button>
        </div>

        <!-- TAB CONTENT: LEETCODE -->
        @if (activeTab() === 'LEETCODE') {
          <section class="animate-slide-up">
            <div class="flex items-baseline justify-between mb-8">
               <div>
                  <h2 class="text-4xl font-black uppercase tracking-tighter italic text-white mb-2">Medals of Honor</h2>
                  <p class="text-noir-400 font-mono text-sm">Your algorithmic conquests and battle scars.</p>
               </div>
               <div class="text-right">
                 <div class="text-3xl font-black text-white">{{ stats().totalSolved || 0 }}</div>
                 <div class="text-xs font-mono text-noir-500 uppercase tracking-widest">Problems Solved</div>
               </div>
            </div>

            @if (leetcodeLoadError()) {
              <div class="mb-8 rounded border border-amber-500/30 bg-amber-950/20 px-4 py-3 text-xs font-mono text-amber-300">
                {{ leetcodeLoadError() }}
              </div>
            }

            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
              @for (medal of medals(); track medal.title) {
                <div
                  class="noir-card p-6 flex flex-col items-center text-center gap-4 transition-all duration-500 group relative overflow-hidden"
                  [class.opacity-40]="!medal.unlocked"
                  [class.grayscale]="!medal.unlocked"
                  [class.hover:opacity-100]="!medal.unlocked"
                  [class.hover:bg-noir-900]="!medal.unlocked"
                >
                  <!-- Medal Icon Circle -->
                  <div
                    class="w-20 h-20 rounded-full flex items-center justify-center border-2 transition-all duration-500 relative z-10"
                    [class.border-amber-700]="medal.tier === 'bronze'"
                    [class.bg-amber-950/20]="medal.tier === 'bronze'"
                    [class.border-slate-400]="medal.tier === 'silver'"
                    [class.bg-slate-900/40]="medal.tier === 'silver'"
                    [class.border-yellow-500]="medal.tier === 'gold'"
                    [class.bg-yellow-950/20]="medal.tier === 'gold'"
                    [class.border-crimson-500]="medal.tier === 'crimson'"
                    [class.bg-crimson-950/20]="medal.tier === 'crimson'"
                    [class.shadow-[0_0_30px_rgba(239,68,68,0.4)]]="medal.tier === 'crimson' && medal.unlocked"
                    [class.group-hover:scale-110]="medal.unlocked"
                  >
                    <lucide-icon [name]="medal.icon" class="w-8 h-8"
                      [class.text-amber-600]="medal.tier === 'bronze'"
                      [class.text-slate-300]="medal.tier === 'silver'"
                      [class.text-yellow-500]="medal.tier === 'gold'"
                      [class.text-crimson-500]="medal.tier === 'crimson'"
                    ></lucide-icon>
                  </div>

                  <!-- Medal Info -->
                  <div class="relative z-10">
                    <h4 class="text-sm font-black uppercase tracking-wider text-noir-100 mb-1">{{ medal.title }}</h4>
                    <p class="text-[10px] text-noir-400 font-mono leading-tight">{{ medal.description }}</p>
                  </div>

                  <!-- Lock Overlay -->
                   @if (!medal.unlocked) {
                    <div class="absolute top-2 right-2 text-noir-600">
                      <lucide-icon name="Lock" class="w-4 h-4"></lucide-icon>
                    </div>
                  }
                </div>
              }
            </div>

            <!-- Stats Breakdown -->
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
               <div class="noir-card p-6 flex flex-col items-center justify-center border-l-4 border-emerald-500">
                  <span class="text-4xl font-black text-white">{{ stats().easySolved || 0 }}</span>
                  <span class="text-xs font-bold uppercase tracking-widest text-emerald-500 mt-2">Initiate (Easy)</span>
               </div>
               <div class="noir-card p-6 flex flex-col items-center justify-center border-l-4 border-yellow-500">
                  <span class="text-4xl font-black text-white">{{ stats().mediumSolved || 0 }}</span>
                  <span class="text-xs font-bold uppercase tracking-widest text-yellow-500 mt-2">Adept (Medium)</span>
               </div>
               <div class="noir-card p-6 flex flex-col items-center justify-center border-l-4 border-crimson-500">
                  <span class="text-4xl font-black text-white">{{ stats().hardSolved || 0 }}</span>
                  <span class="text-xs font-bold uppercase tracking-widest text-crimson-500 mt-2">Master (Hard)</span>
               </div>
            </div>
          </section>
        }

        <!-- TAB CONTENT: GITHUB -->
        @if (activeTab() === 'GITHUB') {
          <section class="animate-slide-up">
            <div class="flex items-baseline justify-between mb-8">
               <div>
                  <h2 class="text-4xl font-black uppercase tracking-tighter italic text-white mb-2">The Armory</h2>
                  <p class="text-noir-400 font-mono text-sm">Open source weaponry forged in the dark.</p>
               </div>
               <div class="text-right">
                 <div class="text-3xl font-black text-white">{{ weapons().length }}</div>
                 <div class="text-xs font-mono text-noir-500 uppercase tracking-widest">Repositories</div>
               </div>
            </div>

            @if (githubLoadError()) {
              <div class="mb-8 rounded border border-crimson-500/30 bg-crimson-950/20 px-4 py-3 text-xs font-mono text-crimson-300">
                {{ githubLoadError() }}
              </div>
            }

            <!-- GITHUB CONFIG START -->
            <div class="noir-card p-6 mb-8 border border-dashed border-noir-700 bg-noir-950/50">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-6 items-end">
                
                <!-- Github Username -->
                <div>
                   <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">
                    GitHub Username
                   </label>
                   <input 
                    type="text" 
                    placeholder="e.g. SavyasachiMishra" 
                    class="w-full bg-black border border-noir-700 text-noir-200 px-4 py-2 font-mono text-sm focus:border-crimson-500 focus:outline-none transition-colors"
                    [value]="githubUsername()"
                    (input)="updateUsernameSignal($event)"
                  />
                </div>

                <!-- Personal Access Token -->
                <div>
                  <label class="block text-xs font-bold uppercase tracking-widest text-noir-500 mb-2">
                    Personal Access Token (PAT)
                  </label>
                  <div class="flex items-center gap-2">
                    <input 
                      type="password" 
                      placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" 
                      class="w-full bg-black border border-noir-700 text-noir-200 px-4 py-2 font-mono text-sm focus:border-crimson-500 focus:outline-none transition-colors"
                      [value]="githubToken()"
                      (input)="updateTokenSignal($event)"
                    />
                    <button 
                      (click)="saveGithubConfig()"
                      [disabled]="isLinking()"
                      class="bg-noir-800 hover:bg-crimson-600 text-white px-6 py-2 uppercase font-black tracking-widest text-xs transition-colors h-[38px] flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      <lucide-icon name="Save" class="w-4 h-4" *ngIf="!isLinking()"></lucide-icon>
                      <lucide-icon name="Loader2" class="w-4 h-4 animate-spin" *ngIf="isLinking()"></lucide-icon>
                      <span>{{ isLinking() ? 'Linking...' : 'Link' }}</span>
                    </button>
                  </div>
                </div>

              </div>
              
              <div class="flex justify-between items-start mt-2">
                 <p class="text-[10px] text-noir-600 font-mono">
                    Required for fetching your repos.
                  </p>
                 <p class="text-[10px] text-noir-600 font-mono text-right">
                    Creates rate-limit exemption. <a href="https://github.com/settings/tokens" target="_blank" class="text-crimson-500 hover:underline">Generate Token</a>
                  </p>
              </div>

              <!-- Status Message -->
              <div *ngIf="linkStatus()" class="mt-4 text-xs font-bold uppercase tracking-widest animate-fade-in"
                   [class.text-emerald-500]="linkStatus()!.success"
                   [class.text-crimson-500]="!linkStatus()!.success">
                {{ linkStatus()!.message }}
              </div>
            </div>
            <!-- GITHUB CONFIG END -->
            
            <!-- LANGUAGE MASTERY CHART -->
            @if (languageStats().length > 0) {
              <div class="mb-12 animate-fade-in delay-100">
                <div class="flex items-baseline justify-between mb-4">
                  <h3 class="text-xl font-bold uppercase tracking-widest text-noir-200">Language Mastery</h3>
                  <span class="text-xs font-mono text-noir-500">{{ weapons().length }} Repositories Analyzed</span>
                </div>
                
                <!-- Progress Bar -->
                <div class="flex h-2 w-full rounded-full overflow-hidden bg-noir-900 mb-4 opacity-80">
                  @for (stat of languageStats(); track stat.name; let i = $index) {
                    <div 
                      class="h-full transition-all duration-1000 ease-out"
                      [style.width.%]="stat.percentage"
                      [style.backgroundColor]="languageColor(stat.name, i)"
                    ></div>
                  }
                </div>

                <!-- Legend -->
                <div class="flex flex-wrap gap-4">
                  @for (stat of languageStats(); track stat.name; let i = $index) {
                    <div class="flex items-center gap-2">
                       <span class="w-2 h-2 rounded-full"
                          [style.backgroundColor]="languageColor(stat.name, i)"
                       ></span>
                       <span class="text-xs font-bold text-noir-300 uppercase">{{ stat.name }}</span>
                       <span class="text-[10px] font-mono text-noir-500">{{ stat.percentage }}%</span>
                    </div>
                  }
                </div>
              </div>
            }

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              @for (weapon of weapons(); track weapon.name) {
                <div 
                  class="noir-card p-8 flex flex-col justify-between group hover:border-crimson-500/50 transition-all duration-300 cursor-pointer"
                  (click)="openRepoDetail(weapon)"
                >
                  <div>
                    <div class="flex justify-between items-start mb-4">
                      <h3 class="text-2xl font-black uppercase tracking-tighter text-white group-hover:text-crimson-500 transition-colors">
                        {{ weapon.name }}
                      </h3>
                      <span class="text-[10px] font-mono border border-noir-700 px-2 py-1 rounded text-noir-400">
                        {{ weapon.language || 'Unknown' }}
                      </span>
                    </div>

                    <p class="text-xs text-noir-500 font-mono leading-relaxed line-clamp-2 min-h-[2.5em]">{{ weapon.description }}</p>
                    <div class="flex items-center gap-6 text-[10px] uppercase font-bold tracking-widest text-noir-600 group-hover:text-white transition-colors">
                      <div class="flex items-center gap-1.5">
                        <lucide-icon name="Star" class="w-3 h-3 text-yellow-500"></lucide-icon>
                        <span>{{ weapon.stars }}</span>
                      </div>
                      <div class="flex items-center gap-1.5">
                        <lucide-icon name="GitFork" class="w-3 h-3 text-emerald-500"></lucide-icon>
                        <span>{{ weapon.forks }}</span>
                      </div>
                      <div class="ml-auto text-[9px] opacity-50">{{ weapon.lastForged | date:'MMM yyyy' }}</div>
                    </div>
                  </div>
                </div>
              }
            </div>
          </section>
        }

        <!-- TAB CONTENT: CODEX -->
        @if (activeTab() === 'CODEX') {
          <section class="animate-slide-up">
            <div class="flex items-baseline justify-between mb-8">
               <div>
                  <h2 class="text-4xl font-black uppercase tracking-tighter italic text-white mb-2">The Codex</h2>
                  <p class="text-noir-400 font-mono text-sm">Live LeetCode profile data: tracked skills, recommendations, and recent solves.</p>
               </div>
            </div>

            @if (codexLoadError()) {
              <div class="mb-8 rounded border border-amber-500/30 bg-amber-950/20 px-4 py-3 text-xs font-mono text-amber-300">
                {{ codexLoadError() }}
              </div>
            }

            @if (codexLoading()) {
              <div class="noir-card p-8 flex items-center gap-3 text-noir-400 font-mono text-sm">
                <lucide-icon name="Loader2" class="w-5 h-5 animate-spin text-crimson-500"></lucide-icon>
                Syncing Codex from your LeetCode-backed profile...
              </div>
            } @else {
              <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10">
                <div class="noir-card p-6 md:col-span-2">
                  <h3 class="text-lg font-black uppercase tracking-widest text-noir-200 mb-4">Tracked Skills</h3>
                  @if (codexSkills().length > 0) {
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      @for (skill of codexSkills(); track skill.id) {
                        <div class="border border-noir-800 rounded p-4 bg-black/40">
                          <p class="text-xs font-bold uppercase tracking-widest text-noir-300">{{ skill.name }}</p>
                          <p class="text-2xl font-black text-white mt-2">{{ skill.problemsSolved || 0 }}</p>
                          <p class="text-[10px] font-mono text-noir-500 uppercase tracking-wider">Problems Solved</p>
                          <p class="text-[10px] font-mono text-noir-500 mt-2">Rating: {{ skill.rating }}/5</p>
                        </div>
                      }
                    </div>
                  } @else {
                    <p class="text-xs font-mono text-noir-500">No language skills are available yet for this account.</p>
                  }
                </div>

                <div class="noir-card p-6 flex flex-col justify-center border-l-4 border-crimson-500">
                  <p class="text-[10px] font-mono uppercase tracking-widest text-noir-500">Recently Solved</p>
                  <p class="text-5xl font-black text-white leading-none mt-2">{{ codexSolvedCount() }}</p>
                  <p class="text-[10px] font-mono uppercase tracking-widest text-noir-500 mt-2">Accepted Algorithms (LeetCode)</p>
                </div>
              </div>

              <div class="noir-card p-6 mb-8">
                <div class="flex items-center justify-between mb-4">
                  <h3 class="text-lg font-black uppercase tracking-widest text-noir-200">Algorithm Mastery</h3>
                  <span class="text-[10px] font-mono uppercase tracking-widest text-noir-500">Solved / Total per tag</span>
                </div>
                @if (algorithmMastery().length > 0) {
                  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    @for (mastery of algorithmMastery(); track mastery.tagSlug) {
                      <div class="border border-noir-800 rounded p-4 bg-black/40">
                        <div class="flex items-start justify-between gap-3">
                          <p class="text-xs font-bold uppercase tracking-widest text-noir-200">{{ mastery.tagName }}</p>
                          <span class="text-[9px] font-mono uppercase tracking-widest text-noir-500">{{ mastery.level }}</span>
                        </div>
                        <div class="mt-3 flex items-end gap-2">
                          <p class="text-2xl font-black text-white leading-none">{{ mastery.masteryPercent | number:'1.0-2' }}%</p>
                          <span class="text-[10px] font-mono uppercase tracking-widest text-noir-500 pb-0.5">{{ mastery.masteryBand }}</span>
                        </div>
                        <p class="text-[10px] font-mono text-noir-500 mt-1">{{ mastery.problemsSolved }} / {{ mastery.totalQuestions || 0 }} questions</p>
                        <div class="h-1.5 w-full bg-noir-900 rounded-full overflow-hidden mt-3">
                          <div class="h-full bg-crimson-600" [style.width.%]="mastery.masteryPercent"></div>
                        </div>
                      </div>
                    }
                  </div>
                } @else {
                  <p class="text-xs font-mono text-noir-500">Mastery data is not available yet for this profile.</p>
                }
              </div>

              <div class="flex flex-wrap items-center gap-3 mb-4">
                <button
                  class="text-[10px] font-bold uppercase tracking-widest px-3 py-1.5 border transition-colors"
                  [class.border-crimson-500]="codexQuestionFilter() === 'ALL'"
                  [class.text-crimson-400]="codexQuestionFilter() === 'ALL'"
                  [class.border-noir-700]="codexQuestionFilter() !== 'ALL'"
                  [class.text-noir-400]="codexQuestionFilter() !== 'ALL'"
                  (click)="setCodexQuestionFilter('ALL')"
                >
                  All ({{ techniques().length }})
                </button>
                <button
                  class="text-[10px] font-bold uppercase tracking-widest px-3 py-1.5 border transition-colors"
                  [class.border-crimson-500]="codexQuestionFilter() === 'SOLVED'"
                  [class.text-crimson-400]="codexQuestionFilter() === 'SOLVED'"
                  [class.border-noir-700]="codexQuestionFilter() !== 'SOLVED'"
                  [class.text-noir-400]="codexQuestionFilter() !== 'SOLVED'"
                  (click)="setCodexQuestionFilter('SOLVED')"
                >
                  Solved ({{ solvedTechniqueCount() }})
                </button>
                <button
                  class="text-[10px] font-bold uppercase tracking-widest px-3 py-1.5 border transition-colors"
                  [class.border-crimson-500]="codexQuestionFilter() === 'UNSOLVED'"
                  [class.text-crimson-400]="codexQuestionFilter() === 'UNSOLVED'"
                  [class.border-noir-700]="codexQuestionFilter() !== 'UNSOLVED'"
                  [class.text-noir-400]="codexQuestionFilter() !== 'UNSOLVED'"
                  (click)="setCodexQuestionFilter('UNSOLVED')"
                >
                  Unsolved ({{ unsolvedTechniqueCount() }})
                </button>

                <div class="ml-auto flex items-center gap-2">
                  <span class="text-[10px] font-mono uppercase tracking-widest text-noir-500">Language</span>
                  <select
                    class="bg-noir-900 border border-noir-700 text-noir-200 text-[10px] font-mono uppercase tracking-widest px-2 py-1.5 focus:border-crimson-500 focus:outline-none"
                    [value]="codexLanguageFilter()"
                    (change)="onCodexLanguageFilterChange($event)"
                  >
                    @for (language of availableCodexLanguages(); track language) {
                      <option [value]="language">{{ language === 'ALL' ? 'All Languages' : language }}</option>
                    }
                  </select>
                </div>
              </div>

              <p class="text-[10px] font-mono text-noir-600 mb-4">Solved/unsolved filter is based on LeetCode accepted-submission visibility for your profile.</p>

              <div class="space-y-4 max-w-5xl">
                @if (filteredTechniques().length === 0) {
                  <div class="noir-card p-6 text-xs font-mono text-noir-500">
                    No questions match this filter yet.
                  </div>
                }

                @for (tech of filteredTechniques(); track tech.name; let i = $index) {
                  <div class="noir-card overflow-hidden group">
                    <button
                      class="w-full flex items-center justify-between p-6 text-left hover:bg-noir-900 transition-colors"
                      (click)="toggleTechnique(i)"
                    >
                      <div class="flex items-center gap-6">
                        <span class="text-xs font-mono text-noir-700 w-6">0{{ i + 1 }}</span>
                        <div>
                          <h3 class="text-xl font-black uppercase tracking-tighter text-noir-100 group-hover:text-crimson-500 transition-colors">
                            {{ tech.name }}
                          </h3>
                          <span class="text-[10px] font-mono text-noir-500 uppercase tracking-widest mt-1 block">{{ tech.pattern }}</span>
                        </div>
                      </div>

                      <div class="flex items-center gap-2 md:gap-4">
                        <span
                          class="text-[9px] font-bold uppercase tracking-widest px-2.5 py-1 border rounded-sm"
                          [class.text-emerald-500]="tech.solvedRecently"
                          [class.border-emerald-500/30]="tech.solvedRecently"
                          [class.text-noir-400]="!tech.solvedRecently"
                          [class.border-noir-700]="!tech.solvedRecently"
                        >
                          {{ tech.solvedRecently ? 'Solved' : 'Pending' }}
                        </span>
                        @if (tech.solvedRecently && tech.dominantLanguage) {
                          <span class="text-[9px] font-bold uppercase tracking-widest px-3 py-1 border rounded-sm text-blue-400 border-blue-500/30">
                            {{ tech.dominantLanguage }}
                          </span>
                        }
                        <span
                          class="text-[9px] font-bold uppercase tracking-widest px-3 py-1 border rounded-sm"
                          [class.text-emerald-500]="tech.difficulty === 'Initiate'"
                          [class.border-emerald-500/30]="tech.difficulty === 'Initiate'"
                          [class.text-yellow-500]="tech.difficulty === 'Adept'"
                          [class.border-yellow-500/30]="tech.difficulty === 'Adept'"
                          [class.text-crimson-500]="tech.difficulty === 'Master'"
                          [class.border-crimson-500/30]="tech.difficulty === 'Master'"
                        >
                          {{ tech.difficulty }}
                        </span>
                        <lucide-icon
                          [name]="expandedTechnique() === i ? 'ChevronUp' : 'ChevronDown'"
                          class="w-5 h-5 text-noir-600 transition-transform duration-300"
                          [class.rotate-180]="expandedTechnique() === i"
                        ></lucide-icon>
                      </div>
                    </button>

                    @if (expandedTechnique() === i) {
                      <div class="border-t border-noir-800 bg-black/50 p-8 space-y-6 animate-fade-in">
                        <p class="text-sm text-noir-300 font-mono leading-relaxed max-w-3xl">{{ tech.description }}</p>
                        @if (tech.solvedRecently && tech.dominantLanguage) {
                          <p class="text-[11px] font-mono uppercase tracking-widest text-noir-400">
                            Mostly solved in {{ tech.dominantLanguage }} ({{ tech.dominantLanguageSharePercent | number:'1.0-0' }}% of recent accepted submissions for this problem)
                          </p>
                        }
                        @if (tech.tags.length > 0) {
                          <div class="flex flex-wrap gap-2">
                            @for (tag of tech.tags; track tag) {
                              <span class="text-[10px] font-mono uppercase tracking-widest text-noir-300 border border-noir-700 px-2 py-1 rounded">{{ tag }}</span>
                            }
                          </div>
                        }
                        @if (tech.url) {
                          <a [href]="tech.url" target="_blank" class="inline-flex items-center gap-2 text-xs font-mono uppercase tracking-widest text-crimson-400 hover:text-crimson-300 transition-colors">
                            <lucide-icon name="ExternalLink" class="w-4 h-4"></lucide-icon>
                            Open on LeetCode
                          </a>
                        }
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </section>
        }

      </div>
    </div>
  `,
  styles: [`
    .writing-mode-vertical {
      writing-mode: vertical-rl;
      text-orientation: mixed;
    }
  `]
})
export class ArsenalComponent implements OnInit {

  activeTab = signal<'LEETCODE' | 'GITHUB' | 'CODEX'>('LEETCODE');
  expandedTechnique = signal<number | null>(null);
  
  weapons = signal<Weapon[]>([]);
  stats = signal<any>({}); // Store raw stats
  githubLoadError = signal<string | null>(null);
  leetcodeLoadError = signal<string | null>(null);
  
  // Base medals config
  medals = signal<Medal[]>([
    { title: 'First Blood', description: 'Solved your first LeetCode problem', icon: 'Sword', tier: 'bronze', unlocked: false },
    { title: 'The Grinder', description: 'Solved 50+ problems', icon: 'Flame', tier: 'silver', unlocked: false },
    { title: 'Century Mark', description: 'Solved 100+ problems', icon: 'Shield', tier: 'gold', unlocked: false },
    { title: 'Polyglot', description: 'Solved in 3+ languages', icon: 'Languages', tier: 'silver', unlocked: true }, // Mocked for now
    { title: 'Hard Mode', description: 'Solved 10+ Hard problems', icon: 'Skull', tier: 'crimson', unlocked: false },
    { title: 'Streak Demon', description: 'Maintained a 30-day streak', icon: 'Zap', tier: 'gold', unlocked: false }, // Mocked
    { title: 'System Architect', description: 'Built & deployed a full-stack app', icon: 'Building2', tier: 'crimson', unlocked: true },
    { title: 'The Ubermensch', description: 'Reached LVL 10 (High Rank)', icon: 'Crown', tier: 'crimson', unlocked: false },
    { title: 'Algorithm Slayer', description: 'Mastered 5+ DSA patterns', icon: 'Target', tier: 'gold', unlocked: true }, 
    { title: 'Night Owl', description: 'Coded past midnight 10+ times', icon: 'Moon', tier: 'bronze', unlocked: true },
  ]);

  unlockedCount = signal(0); // Computed in effect or update

  techniques = signal<Technique[]>([]);
  codexSkills = signal<LanguageSkill[]>([]);
  codexSolvedCount = signal(0);
  codexLoadError = signal<string | null>(null);
  codexLoading = signal(false);
  codexQuestionFilter = signal<'ALL' | 'SOLVED' | 'UNSOLVED'>('ALL');
  codexLanguageFilter = signal<string>('ALL');
  algorithmMastery = signal<AlgorithmMastery[]>([]);
  private readonly languagePalette = [
    '#dc2626', // red
    '#2563eb', // blue
    '#d97706', // amber
    '#059669', // emerald
    '#7c3aed', // violet
    '#0d9488', // teal
    '#c026d3', // fuchsia
    '#ea580c', // orange
    '#4f46e5', // indigo
    '#ca8a04'  // yellow
  ];
  filteredTechniques = computed(() => {
    const filter = this.codexQuestionFilter();
    const language = this.codexLanguageFilter();
    const items = this.techniques();
    let statusFiltered = items;
    if (filter === 'SOLVED') {
      statusFiltered = items.filter((item) => item.solvedRecently);
    } else if (filter === 'UNSOLVED') {
      statusFiltered = items.filter((item) => !item.solvedRecently);
    }

    if (language === 'ALL') {
      return statusFiltered;
    }

    return statusFiltered.filter((item) => item.dominantLanguage === language);
  });
  availableCodexLanguages = computed(() => {
    const set = new Set<string>();
    for (const item of this.techniques()) {
      if (item.dominantLanguage) {
        set.add(item.dominantLanguage);
      }
    }
    return ['ALL', ...Array.from(set).sort((a, b) => a.localeCompare(b))];
  });
  solvedTechniqueCount = computed(() => this.techniques().filter((item) => item.solvedRecently).length);
  unsolvedTechniqueCount = computed(() => this.techniques().filter((item) => !item.solvedRecently).length);

  toggleTechnique(index: number) {
    this.expandedTechnique.update(current => current === index ? null : index);
  }

  setCodexQuestionFilter(filter: 'ALL' | 'SOLVED' | 'UNSOLVED') {
    this.codexQuestionFilter.set(filter);
    this.expandedTechnique.set(null);
  }

  setCodexLanguageFilter(language: string) {
    this.codexLanguageFilter.set(language || 'ALL');
    this.expandedTechnique.set(null);
  }

  onCodexLanguageFilterChange(event: Event) {
    const select = event.target as HTMLSelectElement | null;
    this.setCodexLanguageFilter(select?.value ?? 'ALL');
  }

  http = inject(HttpClient);
  platformId = inject(PLATFORM_ID);
  
  constructor() {}

  ngOnInit() {
    console.log('Arsenal Component v2.1 Loaded');
    if (isPlatformBrowser(this.platformId)) {
      this.checkMedals();
      this.startMedalCheckTimer();
    }
  }

  // --- Language Stats Logic ---
  languageStats = signal<{ name: string; count: number; percentage: number }[]>([]);

  calculateLanguageStats(weapons: Weapon[]) {
    const stats: Record<string, number> = {};
    let total = 0;

    weapons.forEach(repo => {
      const lang = repo.language || 'Unknown';
      stats[lang] = (stats[lang] || 0) + 1;
      total++;
    });

    if (total === 0) {
      this.languageStats.set([]);
      return;
    }

    const result = Object.entries(stats)
      .map(([name, count]) => ({
        name,
        count,
        percentage: Math.round((count / total) * 100)
      }))
      .sort((a, b) => b.count - a.count); // Sort by most used

    this.languageStats.set(result);
  }

  fetchWeapons() {
    this.githubLoadError.set(null);

    this.http.get<Weapon[]>('/api/students/me/github-repos').subscribe({
      next: (data) => {
        const repos = Array.isArray(data) ? data : [];
        this.weapons.set(repos);
        this.calculateLanguageStats(repos);
      },
      error: (err) => {
        console.error('Failed to fetch weapons', err);
        this.weapons.set([]);
        this.calculateLanguageStats([]);

        if (err.status === 401 || err.status === 403) {
          this.githubLoadError.set('GitHub access is unavailable. Link your GitHub username/token below and retry.');
          return;
        }

        this.githubLoadError.set('Could not load GitHub repositories right now. Please try again shortly.');
      }
    });
  }

  // --- Selected Repo Logic ---
  selectedRepo = signal<Weapon | null>(null);
  isLoadingDetails = signal(false);
  repoLanguages = signal<{ name: string; percentage: number }[]>([]);
  repoSkeleton = signal<{ path: string; type: string }[]>([]);

  openRepoDetail(repo: Weapon) {
    this.selectedRepo.set(repo);
    this.isLoadingDetails.set(true);
    this.repoLanguages.set([]);
    this.repoSkeleton.set([]);

    // 1. Fetch Languages
    this.http.get<Record<string, number>>(`/api/students/me/github-repos/${repo.name}/languages`).subscribe({
      next: (langs) => {
        const total = Object.values(langs).reduce((a, b) => a + b, 0);
        const formatted = Object.entries(langs)
          .map(([name, bytes]) => ({
             name,
             percentage: total > 0 ? Math.round((bytes / total) * 100) : 0
          }))
          .sort((a, b) => b.percentage - a.percentage);
        this.repoLanguages.set(formatted);
      },
      error: (e) => console.error('Lang fetch failed', e)
    });

    // 2. Fetch Skeleton
    this.http.get<any[]>(`/api/students/me/github-repos/${repo.name}/skeleton`).subscribe({
      next: (tree) => {
         this.repoSkeleton.set(tree);
         this.isLoadingDetails.set(false);
      },
      error: (e) => {
         console.error('Skeleton fetch failed', e);
         this.isLoadingDetails.set(false);
      }
    });
  }

  closeRepoDetail() {
    this.selectedRepo.set(null);
  }


  
  fetchLeetCodeStats() {
    this.leetcodeLoadError.set(null);

    this.http.get<any>('/api/students/me/leetcode-stats').subscribe({
      next: (data) => {
        const safeData = data ?? {};
        this.stats.set(safeData);
        this.updateMedals(safeData);
      },
      error: (err) => {
        console.error('Failed to fetch LeetCode stats', err);
        this.stats.set({});
        this.updateMedals({});

        if (err.status === 401 || err.status === 403) {
          this.leetcodeLoadError.set('LeetCode stats are unavailable for this session. Please log in again if this continues.');
          return;
        }

        this.leetcodeLoadError.set('Could not load LeetCode stats right now. Please try again shortly.');
      }
    });
  }

  fetchCodexData() {
    this.codexLoading.set(true);
    this.codexLoadError.set(null);
    this.expandedTechnique.set(null);

    forkJoin({
      stats: this.http.get<any>('/api/students/me/leetcode-stats').pipe(
        catchError((err) => {
          this.handleCodexLoadError(err);
          return of({});
        })
      ),
      skills: this.http.get<LanguageSkill[]>('/api/students/me/language-skills').pipe(
        catchError((err) => {
          this.handleCodexLoadError(err);
          return of([]);
        })
      ),
      questions: this.http.get<CommonQuestion[]>('/api/students/me/common-questions').pipe(
        catchError((err) => {
          this.handleCodexLoadError(err);
          return of([]);
        })
      )
    }).subscribe({
      next: ({ stats, skills, questions }) => {
        const recentlySolvedTitles = this.extractRecentlySolvedTitles(stats);
        const solvedLanguageByTitle = this.extractSolvedLanguageByTitle(stats);
        const allQuestions = Array.isArray(questions) ? questions : [];
        const totalSolved = Number(stats?.totalSolved ?? stats?.allSolved ?? 0);
        const mappedTechniques = allQuestions
          .map((question) => this.toTechnique(question, recentlySolvedTitles, solvedLanguageByTitle))
          .filter((technique): technique is Technique => technique !== null)
          .sort((a, b) => {
            if (a.solvedRecently !== b.solvedRecently) {
              return Number(b.solvedRecently) - Number(a.solvedRecently);
            }
            return b.recommendationScore - a.recommendationScore;
          });

        const languageSkills = (Array.isArray(skills) ? skills : []).sort(
          (a, b) => (b.problemsSolved || 0) - (a.problemsSolved || 0)
        );
        const mastery = this.normalizeAlgorithmMastery(stats?.algorithmMastery);
        const solvedInDisplayedSet = mappedTechniques.filter((technique) => technique.solvedRecently).length;

        this.techniques.set(mappedTechniques);
        this.codexSkills.set(languageSkills);
        this.algorithmMastery.set(mastery);
        this.codexSolvedCount.set(totalSolved > 0 ? totalSolved : solvedInDisplayedSet);
        const activeLanguage = this.codexLanguageFilter();
        if (activeLanguage !== 'ALL') {
          const hasLanguage = mappedTechniques.some((technique) => technique.dominantLanguage === activeLanguage);
          if (!hasLanguage) {
            this.codexLanguageFilter.set('ALL');
          }
        }
        this.codexLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to fetch Codex data', err);
        if (!this.codexLoadError()) {
          this.codexLoadError.set('Could not load Codex data right now. Please try again shortly.');
        }
        this.techniques.set([]);
        this.codexSkills.set([]);
        this.algorithmMastery.set([]);
        this.codexSolvedCount.set(0);
        this.codexLoading.set(false);
      }
    });
  }

  private handleCodexLoadError(err: any) {
    console.error('Codex request failed', err);
    if (this.codexLoadError()) {
      return;
    }

    if (err?.status === 401 || err?.status === 403) {
      this.codexLoadError.set('Codex data is unavailable for this session. Please log in again if this continues.');
      return;
    }

    this.codexLoadError.set('Could not load Codex data right now. Please try again shortly.');
  }

  private extractRecentlySolvedTitles(stats: any): Set<string> {
    const titles = new Set<string>();
    const submissions = Array.isArray(stats?.recentAcSubmissions)
      ? (stats.recentAcSubmissions as RecentAcSubmission[])
      : [];

    submissions.forEach((submission) => {
      const title = typeof submission?.title === 'string' ? submission.title.trim().toLowerCase() : '';
      if (title) {
        titles.add(title);
      }
    });

    return titles;
  }

  private extractSolvedLanguageByTitle(stats: any): Map<string, { language: string; submissions: number; sharePercent: number }> {
    const result = new Map<string, { language: string; submissions: number; sharePercent: number }>();
    const submissions = Array.isArray(stats?.recentAcSubmissions)
      ? (stats.recentAcSubmissions as RecentAcSubmission[])
      : [];

    const countByTitle = new Map<string, Map<string, number>>();
    for (const submission of submissions) {
      const title = typeof submission?.title === 'string' ? submission.title.trim().toLowerCase() : '';
      if (!title) {
        continue;
      }

      const language = typeof submission?.langName === 'string' && submission.langName.trim()
        ? submission.langName.trim()
        : (typeof submission?.lang === 'string' && submission.lang.trim() ? submission.lang.trim() : 'Unknown');

      const languageMap = countByTitle.get(title) ?? new Map<string, number>();
      languageMap.set(language, (languageMap.get(language) ?? 0) + 1);
      countByTitle.set(title, languageMap);
    }

    for (const [title, languageMap] of countByTitle.entries()) {
      let dominantLanguage = '';
      let dominantCount = 0;
      let totalCount = 0;
      for (const [language, count] of languageMap.entries()) {
        totalCount += count;
        if (count > dominantCount) {
          dominantLanguage = language;
          dominantCount = count;
        }
      }

      if (!dominantLanguage || totalCount <= 0) {
        continue;
      }

      result.set(title, {
        language: dominantLanguage,
        submissions: dominantCount,
        sharePercent: Math.round((dominantCount * 10000) / totalCount) / 100
      });
    }

    return result;
  }

  private normalizeAlgorithmMastery(payload: any): AlgorithmMastery[] {
    if (!Array.isArray(payload)) {
      return [];
    }

    return payload
      .map((entry): AlgorithmMastery | null => {
        const tagName = typeof entry?.tagName === 'string' ? entry.tagName : '';
        const tagSlug = typeof entry?.tagSlug === 'string' ? entry.tagSlug : '';
        if (!tagName || !tagSlug) {
          return null;
        }

        const problemsSolved = Number(entry?.problemsSolved ?? 0);
        const totalQuestions = Number(entry?.totalQuestions ?? 0);
        const masteryPercentRaw = Number(entry?.masteryPercent ?? 0);
        const masteryPercent = Number.isFinite(masteryPercentRaw) ? Math.max(0, Math.min(100, masteryPercentRaw)) : 0;

        return {
          tagName,
          tagSlug,
          level: typeof entry?.level === 'string' ? entry.level : 'Unknown',
          problemsSolved: Number.isFinite(problemsSolved) ? problemsSolved : 0,
          totalQuestions: Number.isFinite(totalQuestions) ? totalQuestions : 0,
          masteryPercent,
          masteryBand: typeof entry?.masteryBand === 'string' ? entry.masteryBand : 'Unstarted'
        };
      })
      .filter((entry): entry is AlgorithmMastery => entry !== null)
      .sort((a, b) => {
        if (a.masteryPercent !== b.masteryPercent) {
          return b.masteryPercent - a.masteryPercent;
        }
        return b.problemsSolved - a.problemsSolved;
      });
  }

  private toTechnique(
    question: CommonQuestion,
    recentlySolvedTitles: Set<string>,
    solvedLanguageByTitle: Map<string, { language: string; submissions: number; sharePercent: number }>
  ): Technique | null {
    const rawName = typeof question?.title === 'string' ? question.title.trim() : '';
    if (!rawName) {
      return null;
    }

    const normalizedName = rawName.toLowerCase();
    const tags = Array.isArray(question?.tags) ? question.tags.map((tag) => String(tag)) : [];
    const matchedTags = Array.isArray(question?.matchedTags) ? question.matchedTags.map((tag) => String(tag)) : [];
    const solvedRecently = recentlySolvedTitles.has(normalizedName);
    const languageInsight = solvedLanguageByTitle.get(normalizedName);
    const recommendationScore = Number(question?.recommendationScore ?? 0);
    const recommended = question?.recommended === true;
    const labelTags = matchedTags.length > 0 ? matchedTags : tags;
    const pattern = labelTags.length > 0 ? labelTags.slice(0, 3).join(' / ') : 'general-problem-solving';
    const recommendationHint = recommended
      ? 'Recommended from your current LeetCode skill profile.'
      : 'Included in your personalized algorithm set.';
    const solvedHint = solvedRecently
      ? 'Recently solved by your account.'
      : 'Not found in your recent accepted submissions.';
    const languageHint = solvedRecently && languageInsight?.language
      ? ` Mostly solved in ${languageInsight.language}.`
      : '';

    return {
      name: rawName,
      pattern,
      difficulty: this.toCodexDifficulty(question?.difficulty),
      description: `${recommendationHint} ${solvedHint}${languageHint}`,
      url: typeof question?.url === 'string' ? question.url : '',
      tags,
      solvedRecently,
      dominantLanguage: languageInsight?.language ?? '',
      dominantLanguageSharePercent: languageInsight?.sharePercent ?? 0,
      recommendationScore
    };
  }

  languageColor(name: string, index: number): string {
    const safeName = (name || '').trim();
    if (!safeName) {
      return '#52525b';
    }

    let hash = 0;
    for (let i = 0; i < safeName.length; i++) {
      hash = ((hash << 5) - hash) + safeName.charCodeAt(i);
      hash |= 0;
    }
    const normalized = Math.abs(hash + index);
    return this.languagePalette[normalized % this.languagePalette.length];
  }

  private toCodexDifficulty(difficulty?: string): 'Initiate' | 'Adept' | 'Master' {
    const value = (difficulty || '').toLowerCase();
    if (value.includes('easy')) {
      return 'Initiate';
    }
    if (value.includes('hard')) {
      return 'Master';
    }
    return 'Adept';
  }
  
  updateMedals(stats: any) {
    const totalSolved = stats.totalSolved ?? stats.allSolved ?? 0;
    const hardSolved = stats.hardSolved || 0;
    // const ranking = stats.ranking || 999999; 

    this.medals.update(currentMedals => {
        return currentMedals.map(medal => {
            // Logic for unlocking specific medals
            if (medal.title === 'First Blood' && totalSolved >= 1) return { ...medal, unlocked: true };
            if (medal.title === 'The Grinder' && totalSolved >= 50) return { ...medal, unlocked: true };
            if (medal.title === 'Century Mark' && totalSolved >= 100) return { ...medal, unlocked: true };
            if (medal.title === 'Hard Mode' && hardSolved >= 10) return { ...medal, unlocked: true };
            // For now, keep others as default (some hardcoded to true in init)
            return medal;
        });
    });
    
    this.unlockedCount.set(this.medals().filter(m => m.unlocked).length);
  }
  
  checkMedals() {
    this.fetchWeapons();
    this.fetchLeetCodeStats();
    this.fetchCodexData();
  }
  
  startMedalCheckTimer() {
    // Poll every 5 minutes to keep stats fresh
    setInterval(() => {
      this.checkMedals();
    }, 5 * 60 * 1000); 
  }

  // --- GitHub Config Logic ---
  githubToken = signal('');
  githubUsername = signal('');
  
  isLinking = signal(false);
  linkStatus = signal<{ success: boolean; message: string } | null>(null);

  updateTokenSignal(event: Event) {
    const input = event.target as HTMLInputElement;
    this.githubToken.set(input.value);
  }

  updateUsernameSignal(event: Event) {
    const input = event.target as HTMLInputElement;
    this.githubUsername.set(input.value);
  }

  saveGithubConfig() {
    const token = this.githubToken();
    const username = this.githubUsername();
    
    // We need at least one to be worth saving, but realistically username is critical
    if (!token && !username) return;

    this.isLinking.set(true);
    this.linkStatus.set(null);

    this.http.post('/api/students/me/github-token', { token, username }).subscribe({
      next: (res) => {
        console.log('Config updated');
        this.githubLoadError.set(null);
        this.fetchWeapons(); // Refresh repos
        this.isLinking.set(false);
        this.linkStatus.set({ success: true, message: 'GitHub Linked! Scanning repositories...' });
        
        setTimeout(() => this.linkStatus.set(null), 3000);
      },
      error: (err) => {
        console.error('Failed to update config', err);
        this.isLinking.set(false);
        this.linkStatus.set({ success: false, message: 'Link Failed. Check info.' });
      }
    });
  }
}
