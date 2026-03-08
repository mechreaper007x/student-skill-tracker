import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  private authService = inject(AuthService);
  
  user = this.authService.currentUser;
  
  isSidebarOpen = signal(true);
  
  navItems = computed(() => {
    const baseItems = [
      { label: 'Command Center', icon: 'LayoutDashboard', route: '/dashboard' },
      { label: 'Will to Power', icon: 'Zap', route: '/duel-arena' },
      { label: 'The Arena', icon: 'Trophy', route: '/leaderboard' },
      { label: 'The Rishi', icon: 'Brain', route: '/advisor' },
      { label: 'Arsenal', icon: 'Package', route: '/arsenal' },
      { label: 'Proving Grounds', icon: 'Swords', route: '/proving-grounds' },
      { label: 'Compiler', icon: 'Terminal', route: '/compiler' },
      { label: 'Cognitive Sprint', icon: 'Brain', route: '/cognitive-sprint' },
      { label: 'Settings', icon: 'Settings', route: '/settings' }
    ];

    if (this.authService.hasAnyRole(['HR', 'INTERVIEWER', 'ADMIN'])) {
      return [
        ...baseItems,
        { label: 'HR Panel', icon: 'Building2', route: '/hr-dashboard' }
      ];
    }

    return baseItems;
  });

  toggleSidebar() {
    this.isSidebarOpen.update(v => !v);
  }

  logout() {
    this.authService.logout();
  }
}
