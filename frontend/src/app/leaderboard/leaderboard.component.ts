import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../core/auth/auth.service';

interface Gladiator {
  id: string;
  rank: number;
  name: string;
  level: number;
  bloomLevel: number;
  xp: number;
  avatar: string;
  isCurrentUser: boolean;
}

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="p-8 space-y-12 animate-fade-in">
      <!-- Header -->
      <div class="text-center md:text-left">
        <h1 class="text-4xl font-bold tracking-tighter text-white uppercase italic flex items-center justify-center md:justify-start gap-4">
           <lucide-icon name="Trophy" class="text-crimson-600 w-10 h-10"></lucide-icon>
           The Arena
        </h1>
        <p class="text-noir-400 font-mono text-sm mt-2">Internal Rankings: Prove your will against the strongest seekers.</p>
      </div>

      @if (loading()) {
        <div class="flex justify-center py-20">
          <div class="text-noir-500 animate-pulse">Loading warriors...</div>
        </div>
      } @else if (gladiators().length === 0) {
        <div class="flex justify-center py-20">
          <div class="text-noir-500">No warriors have entered the arena yet.</div>
        </div>
      } @else {
        <!-- Top 3 Podium -->
        <div class="flex justify-center items-end gap-6 mb-12 min-h-[220px]">
          
          <!-- Rank 2 -->
          @if (gladiators().length >= 2) {
            <div class="flex flex-col items-center gap-3 animate-float-delayed-1">
               <div class="w-16 h-16 rounded-full border-2 border-noir-700 bg-noir-800 flex items-center justify-center text-xl font-bold text-noir-400 shadow-lg">
                 {{ gladiators()[1].avatar }}
               </div>
               <div class="w-24 h-32 bg-noir-900 rounded-t-2xl border-x border-t border-noir-800 flex flex-col items-center justify-start py-6 shadow-[0_-10px_20px_rgba(0,0,0,0.4)]">
                 <span class="text-2xl font-bold text-noir-500">2</span>
                 <span class="text-[10px] text-noir-400 font-bold">LVL {{ gladiators()[1].level }}</span>
                 <lucide-icon name="Medal" class="w-6 h-6 text-noir-400 mt-2"></lucide-icon>
               </div>
            </div>
          }

          <!-- Rank 1 -->
          @if (gladiators().length >= 1) {
            <div class="flex flex-col items-center gap-4 z-10 animate-float">
               <div class="relative">
                 <div class="absolute -top-10 left-1/2 -translate-x-1/2 text-crimson-500 animate-pulse">
                   <lucide-icon name="Award" class="w-12 h-12"></lucide-icon>
                 </div>
                 <div class="w-24 h-24 rounded-full border-4 border-crimson-500/30 bg-noir-800 flex items-center justify-center text-3xl font-bold text-white shadow-[0_0_30px_rgba(220,38,38,0.2)]">
                   {{ gladiators()[0].avatar }}
                 </div>
               </div>
               <div class="w-32 h-48 bg-gradient-to-b from-crimson-950/20 to-noir-900 rounded-t-2xl border-x border-t border-crimson-500/30 flex flex-col items-center justify-start py-8 relative overflow-hidden shadow-[0_-15px_30px_rgba(0,0,0,0.6)]">
                 <div class="absolute inset-x-0 top-0 h-1 bg-crimson-600"></div>
                 <span class="text-5xl font-bold text-white tracking-tighter">1</span>
                 <span class="text-xs text-crimson-400 mt-2 font-mono font-bold tracking-widest uppercase">LVL {{ gladiators()[0].level }}</span>
                 <span class="text-[10px] text-noir-400 font-mono mt-1 italic">Bloom L{{ gladiators()[0].bloomLevel }}</span>
               </div>
            </div>
          }

          <!-- Rank 3 -->
          @if (gladiators().length >= 3) {
            <div class="flex flex-col items-center gap-3 animate-float-delayed-2">
               <div class="w-16 h-16 rounded-full border-2 border-noir-700 bg-noir-800 flex items-center justify-center text-xl font-bold text-noir-500 shadow-lg">
                 {{ gladiators()[2].avatar }}
               </div>
               <div class="w-20 h-24 bg-noir-900 rounded-t-2xl border-x border-t border-noir-800 flex flex-col items-center justify-start py-6 shadow-[0_-10px_20px_rgba(0,0,0,0.4)]">
                 <span class="text-2xl font-bold text-noir-600">3</span>
                 <span class="text-[10px] text-noir-500 font-bold">LVL {{ gladiators()[2].level }}</span>
                 <lucide-icon name="Medal" class="w-6 h-6 text-noir-600 mt-2"></lucide-icon>
               </div>
            </div>
          }

        </div>

        <!-- List -->
        <div class="noir-card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm text-noir-400">
              <thead class="bg-noir-950/50 text-[10px] uppercase font-bold tracking-[0.2em] text-noir-600 border-b border-noir-800">
                <tr>
                  <th class="px-8 py-5">Rank</th>
                  <th class="px-8 py-5">Seeker</th>
                  <th class="px-8 py-5">Level</th>
                  <th class="px-8 py-5">Cognition</th>
                  <th class="px-8 py-5 text-right">Potency (XP)</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-noir-800">
                @for (user of gladiators(); track user.id) {
                  <tr class="hover:bg-noir-900/50 transition-all group cursor-default">
                    <td class="px-8 py-5 font-mono">
                      <span 
                        class="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-noir-900 text-xs border border-noir-800 group-hover:border-crimson-500/30 transition-colors"
                        [class.text-crimson-500]="user.rank === 1"
                        [class.text-noir-100]="user.rank === 2 || user.rank === 3"
                        [class.text-noir-500]="user.rank > 3"
                      >
                        #{{ user.rank }}
                      </span>
                    </td>
                    <td class="px-8 py-5 font-medium text-noir-200 group-hover:text-white flex items-center gap-4">
                        <div class="w-10 h-10 rounded-xl bg-noir-900 flex items-center justify-center text-xs font-bold ring-1 ring-noir-800 group-hover:ring-crimson-500/50 transition-all">
                          {{ user.avatar }}
                        </div>
                        {{ user.name }}
                        @if (user.isCurrentUser) { <span class="text-[9px] bg-crimson-600/10 text-crimson-500 px-2 py-0.5 rounded-full ml-3 border border-crimson-600/20 font-bold tracking-widest uppercase">YOU</span> }
                    </td>
                    <td class="px-8 py-5">
                      <span class="text-noir-300 font-bold">LVL {{ user.level }}</span>
                    </td>
                    <td class="px-8 py-5">
                      <span class="text-[10px] px-2 py-1 bg-noir-800 rounded border border-noir-700 font-mono">Bloom L{{ user.bloomLevel }}</span>
                    </td>
                    <td class="px-8 py-5 text-right font-mono text-crimson-600 font-bold group-hover:text-crimson-400 transition-colors">{{ user.xp | number }} XP</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .animate-float { animation: float 6s ease-in-out infinite; }
    .animate-float-delayed-1 { animation: float 6s ease-in-out infinite 1s; }
    .animate-float-delayed-2 { animation: float 6s ease-in-out infinite 2s; }
    
    @keyframes float {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(-12px); }
    }
  `]
})
export class LeaderboardComponent implements OnInit {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  
  gladiators = signal<Gladiator[]>([]);
  loading = signal(true);

  ngOnInit() {
    this.fetchLeaderboard();
  }

  private fetchLeaderboard() {
    this.loading.set(true);
    this.http.get<any[]>('/api/students/leaderboard').subscribe({
      next: (data) => {
        const currentUserEmail = this.authService.currentUser()?.email;
        
        const gladiators: Gladiator[] = data.map((entry, index) => ({
          id: String(index + 1),
          rank: entry.ranking || (index + 1),
          name: entry.name || entry.leetcodeUsername,
          avatar: this.generateAvatar(entry.name || entry.leetcodeUsername),
          level: entry.level || 1,
          bloomLevel: entry.bloomLevel || 1,
          xp: entry.xp || 0,
          isCurrentUser: entry.email === currentUserEmail
        }));
        
        this.gladiators.set(gladiators);
        this.loading.set(false);
      },
      error: () => {
        this.gladiators.set([]);
        this.loading.set(false);
      }
    });
  }

  private generateAvatar(name: string): string {
    if (!name) return '??';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }
}

