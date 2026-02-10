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
      return { username: 'Guest', level: 1, xp: 0 };
    }
    return {
      username: user.leetcodeUsername || user.name || user.email || 'Guest',
      level: dashboard?.level ?? user.level ?? 1,
      xp: dashboard?.xp ?? user.xp ?? 0
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

    // Fallback: use dashboard skill scores if no language skills loaded yet
    const dashboard = this.dashboardData();
    const skillData = dashboard?.skillData;
    if (!skillData) return [];

    const scoreToRating = (score: number | undefined): number => {
      if (!score || score === 0) return 1;
      return Math.min(5, Math.max(1, Math.ceil(score / 20)));
    };

    return [
      { skillName: 'Problem Solving', rating: scoreToRating(skillData.problemSolvingScore) },
      { skillName: 'Algorithms', rating: scoreToRating(skillData.algorithmsScore) },
      { skillName: 'Data Structures', rating: scoreToRating(skillData.dataStructuresScore) }
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
      advice: "Your current XP reflects a strong dedication to core fundamentals. Based on your profile, focusing on 'Cloud Deployment' and 'Distributed Systems' would provide the highest synergistic value to your current Spring Boot expertise.",
      recommendedSkills: ['AWS', 'Docker', 'Kubernetes', 'Microservices']
    };
  });

  advisorLoading = signal(false);

  xpPercentage = computed(() => {
    const currentXp = this.student().xp;
    const xpInLevel = currentXp % 1000;
    return (xpInLevel / 1000) * 100;
  });

  // Problem Solving Stats (LeetCode Style)
  problemStats = computed(() => {
    const dashboard = this.dashboardData();
    const skillData = dashboard?.skillData;

    const total = skillData?.totalProblemsSolved ?? 0;
    const easy = skillData?.easyProblems ?? 0;
    const medium = skillData?.mediumProblems ?? 0;
    const hard = skillData?.hardProblems ?? 0;

    return {
      total,
      easy,
      medium,
      hard,
      // Calculate percentages relative to total solved (or default to 0 to avoid NaN)
      // Visual scaling: if total is 0, all bars are 0.
      easyPct: total > 0 ? (easy / total) * 100 : 0,
      mediumPct: total > 0 ? (medium / total) * 100 : 0,
      hardPct: total > 0 ? (hard / total) * 100 : 0
    };
  });

  private platformId = inject(PLATFORM_ID);

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.fetchDashboardData();
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
}
