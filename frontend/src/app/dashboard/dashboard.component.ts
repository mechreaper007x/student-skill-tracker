import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { SkillStore } from '../skills/skill.store';

interface Student {
  username: string;
  level: number;
  xp: number;
}

interface SkillDisplay {
  skillName: string;
  rating: number;
}

interface AdvisorResult {
  advice: string;
  recommendedSkills: string[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private http = inject(HttpClient);
  readonly skillStore = inject(SkillStore);

  // Dashboard data fetched from backend
  private dashboardData = signal<any>(null);
  globalRank = signal<number | null>(null);
  masteryHeatmap = signal<any[]>([]);

  greeting = computed(() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good Morning';
    if (hour < 18) return 'Good Afternoon';
    return 'Good Evening';
  });

  student = computed(() => {
    const user = this.authService.currentUser();
    const dashboard = this.dashboardData();
    if (!user) {
      return { username: 'Guest', level: 1, xp: 0, duelWins: 0, bloomLevel: 1 };
    }
    return {
      username: user.leetcodeUsername || user.name || user.email || 'Guest',
      level: dashboard?.level ?? user.level ?? 1,
      xp: dashboard?.xp ?? user.xp ?? 0,
      duelWins: dashboard?.duelWins ?? user.duelWins ?? 0,
      bloomLevel: dashboard?.highestBloomLevel ?? user.highestBloomLevel ?? 1
    };
  });

  // Language skills from SkillStore (fetched from /api/students/me/language-skills)
  skills = computed(() => {
    const storeSkills = this.skillStore.skills();
    if (storeSkills.length > 0) {
      return storeSkills.map(s => ({
        skillName: s.name,
        rating: s.rating
      }));
    }

    // Fallback: use internal skill categories if no language skills loaded yet
    return [
      { skillName: 'Problem Solving', rating: Math.min(5, Math.ceil((this.student().level) / 2)) },
      { skillName: 'Cognitive Depth', rating: this.student().bloomLevel || 1 },
      { skillName: 'Duel Prowess', rating: Math.min(5, Math.ceil((this.student().duelWins || 1) / 5)) }
    ];
  });

  // Use AI advice from backend if available, otherwise use mock
  currentAdvice = computed(() => {
    const dashboard = this.dashboardData();
    const aiAdvice = dashboard?.skillData?.aiAdvice;
    
    if (aiAdvice) {
      return {
        advice: aiAdvice,
        recommendedSkills: []
      };
    }
    
    // Fallback mock data
    return {
      advice: "Your performance in the Duel Arena is being analyzed. Focus on advancing through Bloom Levels to unlock higher-tier cognitive strategies.",
      recommendedSkills: ['Algorithmic Logic', 'Pattern Recognition', 'System 2 Thinking']
    };
  });

  advisorLoading = signal(false);

  xpPercentage = computed(() => {
    const currentXp = this.student().xp;
    return (currentXp / 1000) * 100;
  });

  // Internal Duel Stats
  duelStats = computed(() => {
    const s = this.student();
    return {
      wins: s.duelWins,
      bloomLevel: s.bloomLevel,
      rank: this.globalRank() || 'N/A'
    };
  });

  private platformId = inject(PLATFORM_ID);

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.fetchDashboardData();
      this.fetchGlobalRank();
      this.fetchMasteryHeatmap();
    }
  }

  private fetchDashboardData() {
    this.http.get<any>('/api/students/me/dashboard').subscribe({
      next: (data) => {
        this.dashboardData.set(data);
      },
      error: (err) => {
        console.error('Failed to fetch dashboard data:', err);
      }
    });
  }

  private fetchGlobalRank() {
    this.http.get<any[]>('/api/students/leaderboard').subscribe({
      next: (data) => {
        const currentUserEmail = this.authService.currentUser()?.email;
        const entry = data.find(e => e.email === currentUserEmail);
        if (entry) {
          this.globalRank.set(entry.ranking);
        }
      }
    });
  }

  private fetchMasteryHeatmap() {
    this.http.get<any[]>('/api/mastery/heatmap').subscribe({
      next: (data) => {
        this.masteryHeatmap.set(data);
      },
      error: (err) => {
        console.error('Failed to fetch mastery heatmap:', err);
      }
    });
  }
}
