import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ShellComponent } from './layout/shell/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'skills',
        loadComponent: () => import('./skills/skill-grid.component').then(m => m.SkillGridComponent)
      },
      {
        path: 'advisor',
        loadComponent: () => import('./advisor/advisor.component').then(m => m.AdvisorComponent)
      },
      {
        path: 'leaderboard',
        loadComponent: () => import('./leaderboard/leaderboard.component').then(m => m.LeaderboardComponent)
      },
      {
        path: 'arsenal',
        loadComponent: () => import('./arsenal/arsenal.component').then(m => m.ArsenalComponent)
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
