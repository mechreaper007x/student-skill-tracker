import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
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
  
  navItems = [
    { label: 'Command Center', icon: 'LayoutDashboard', route: '/dashboard' },
    { label: 'Will to Power', icon: 'Zap', route: '/skills' },
    { label: 'The Arena', icon: 'Trophy', route: '/leaderboard' },
    { label: 'The Rishi', icon: 'Brain', route: '/advisor' },
    { label: 'Arsenal', icon: 'Package', route: '/arsenal' },
  ];

  toggleSidebar() {
    this.isSidebarOpen.update(v => !v);
  }

  logout() {
    this.authService.logout();
  }
}
