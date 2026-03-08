import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
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
        redirectTo: 'duel-arena',
        pathMatch: 'full'
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
        path: 'proving-grounds',
        loadComponent: () => import('./proving-grounds/proving-grounds.component').then(m => m.ProvingGroundsComponent)
      },
      {
        path: 'duel-arena',
        loadComponent: () => import('./duel-arena/duel-arena.component').then(m => m.DuelArenaComponent)
      },
      {
        path: 'cognitive-sprint',
        loadComponent: () => import('./cognitive-sprint/cognitive-sprint.component').then(m => m.CognitiveSprintComponent)
      },
      {
        path: 'compiler',
        loadComponent: () => import('./battle-station/battle-station.component').then(m => m.BattleStationComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent)
      },
      {
        path: 'hr-dashboard',
        canActivate: [roleGuard],
        data: { roles: ['HR', 'INTERVIEWER', 'ADMIN'] },
        loadComponent: () => import('./hr-dashboard/hr-dashboard.component').then(m => m.HrDashboardComponent)
      },
      {
        path: 'interviewer-workbench/:candidateId',
        canActivate: [roleGuard],
        data: { roles: ['INTERVIEWER', 'HR', 'ADMIN'] },
        loadComponent: () => import('./interviewer-workbench/interviewer-workbench.component')
          .then(m => m.InterviewerWorkbenchComponent)
      },
      {
        path: 'battle-station',
        redirectTo: 'compiler',
        pathMatch: 'full'
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
