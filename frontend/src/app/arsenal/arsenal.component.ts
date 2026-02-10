import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

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
  snippet: string;
  description: string;
}

interface RepoDetail {
  languages: Record<string, number>;
  skeleton: { path: string; type: string }[];
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
                  @for (stat of languageStats(); track stat.name) {
                    <div 
                      class="h-full transition-all duration-1000 ease-out"
                      [style.width.%]="stat.percentage"
                      [class.bg-crimson-600]="stat.name === 'Java'"
                      [class.bg-blue-500]="stat.name === 'TypeScript'"
                      [class.bg-yellow-400]="stat.name === 'JavaScript'"
                      [class.bg-orange-500]="stat.name === 'HTML' || stat.name === 'CSS'"
                      [class.bg-purple-500]="stat.name === 'C#'"
                      [class.bg-emerald-500]="stat.name === 'Python'"
                      [class.bg-noir-600]="!['Java', 'TypeScript', 'JavaScript', 'HTML', 'CSS', 'C#', 'Python'].includes(stat.name)"
                    ></div>
                  }
                </div>

                <!-- Legend -->
                <div class="flex flex-wrap gap-4">
                  @for (stat of languageStats(); track stat.name) {
                    <div class="flex items-center gap-2">
                       <span class="w-2 h-2 rounded-full"
                          [class.bg-crimson-600]="stat.name === 'Java'"
                          [class.bg-blue-500]="stat.name === 'TypeScript'"
                          [class.bg-yellow-400]="stat.name === 'JavaScript'"
                          [class.bg-orange-500]="stat.name === 'HTML' || stat.name === 'CSS'"
                          [class.bg-purple-500]="stat.name === 'C#'"
                          [class.bg-emerald-500]="stat.name === 'Python'"
                          [class.bg-noir-600]="!['Java', 'TypeScript', 'JavaScript', 'HTML', 'CSS', 'C#', 'Python'].includes(stat.name)"
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
                  <p class="text-noir-400 font-mono text-sm">Forbidden techniques and algorithmic patterns.</p>
               </div>
            </div>

            <div class="space-y-4 max-w-4xl">
              @for (tech of techniques(); track tech.name; let i = $index) {
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
                    
                    <div class="flex items-center gap-4">
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
                      <p class="text-sm text-noir-300 font-mono leading-relaxed max-w-2xl">{{ tech.description }}</p>
                      <div class="relative group/code">
                        <div class="absolute -top-3 left-4 px-2 bg-noir-900 text-[10px] text-noir-500 uppercase tracking-widest border border-noir-800">JavaScript</div>
                        <pre class="bg-noir-950 p-6 rounded border border-noir-800 overflow-x-auto"><code class="text-sm font-mono text-emerald-400 leading-relaxed">{{ tech.snippet }}</code></pre>
                      </div>
                    </div>
                  }
                </div>
              }
            </div>
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

  techniques = signal<Technique[]>([
    {
      name: 'Sliding Window',
      pattern: 'Two Pointer / Window',
      difficulty: 'Adept',
      description: 'Maintain a dynamic window over a sequence to find subarrays/substrings satisfying a condition. Shrink from left, expand from right.',
      snippet: `function maxSubarraySum(arr, k) {\n  let maxSum = 0, windowSum = 0;\n  for (let i = 0; i < arr.length; i++) {\n    windowSum += arr[i];\n    if (i >= k - 1) {\n      maxSum = Math.max(maxSum, windowSum);\n      windowSum -= arr[i - (k - 1)];\n    }\n  }\n  return maxSum;\n}`
    },
    {
      name: 'Binary Search',
      pattern: 'Divide & Conquer',
      difficulty: 'Initiate',
      description: 'Eliminate half the search space each iteration. Works on sorted arrays or monotonic functions. O(log n).',
      snippet: `function binarySearch(arr, target) {\n  let lo = 0, hi = arr.length - 1;\n  while (lo <= hi) {\n    const mid = Math.floor((lo + hi) / 2);\n    if (arr[mid] === target) return mid;\n    if (arr[mid] < target) lo = mid + 1;\n    else hi = mid - 1;\n  }\n  return -1;\n}`
    },
    {
      name: 'Backtracking',
      pattern: 'Recursion / DFS',
      difficulty: 'Master',
      description: 'Explore all possible paths by building candidates incrementally and abandoning ("backtracking") when a constraint is violated.',
      snippet: `function permute(nums) {\n  const result = [];\n  function backtrack(path, remaining) {\n    if (remaining.length === 0) {\n      result.push([...path]);\n      return;\n    }\n    for (let i = 0; i < remaining.length; i++) {\n      path.push(remaining[i]);\n      backtrack(path, [...remaining.slice(0,i), ...remaining.slice(i+1)]);\n      path.pop();\n    }\n  }\n  backtrack([], nums);\n  return result;\n}`
    }
  ]);

  toggleTechnique(index: number) {
    this.expandedTechnique.update(current => current === index ? null : index);
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
  
  updateMedals(stats: any) {
    const totalSolved = stats.totalSolved || 0;
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
