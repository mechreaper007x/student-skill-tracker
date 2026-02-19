import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, RouterModule],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-noir-950 p-6">
      <div class="w-full max-w-md animate-fade-in">
        <!-- Brand -->
        <div class="flex flex-col items-center mb-10">
          <div class="w-16 h-16 rounded-2xl bg-crimson-600 flex items-center justify-center shadow-lg shadow-crimson-900/40 mb-4 animate-float">
            <lucide-icon name="Terminal" class="w-10 h-10 text-white"></lucide-icon>
          </div>
          <h1 class="text-3xl font-bold tracking-tighter text-white">STUDENT TRACKER</h1>
          <p class="text-noir-400 mt-2 font-mono">The Will to Power. The Code to Conquer.</p>
        </div>

        <!-- Form Card -->
        <div class="noir-card p-8">
          <h2 class="text-xl font-bold text-white mb-6">Ascend</h2>
          
          <form (ngSubmit)="onSubmit()" #loginForm="ngForm" class="space-y-6">
            <div class="space-y-2">
              <label class="text-xs font-semibold uppercase tracking-widest text-noir-500 ml-1">Email Abyss</label>
              <div class="relative">
                <lucide-icon name="Mail" class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-noir-500"></lucide-icon>
                <input 
                  type="email" 
                  name="email"
                  [ngModel]="email()" 
                  (ngModelChange)="email.set($event)"
                  required
                  placeholder="name@example.com"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-3 pl-10 pr-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/50 focus:ring-1 focus:ring-crimson-500/50 transition-all font-mono"
                >
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-xs font-semibold uppercase tracking-widest text-noir-500 ml-1">Password Cipher</label>
              <div class="relative">
                <lucide-icon name="Lock" class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-noir-500"></lucide-icon>
                <input 
                  type="password" 
                  name="password"
                  [ngModel]="password()" 
                  (ngModelChange)="password.set($event)"
                  required
                  placeholder="••••••••"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-3 pl-10 pr-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-crimson-500/50 focus:ring-1 focus:ring-crimson-500/50 transition-all font-mono"
                >
              </div>
            </div>

            <div *ngIf="error()" class="p-3 bg-crimson-500/10 border border-crimson-500/20 rounded-lg text-crimson-400 text-sm font-mono animate-shake">
              {{ error() }}
            </div>

            <button 
              type="submit"
              [disabled]="loading() || !loginForm.valid"
              class="w-full bg-crimson-600 hover:bg-crimson-500 text-white font-bold py-3 rounded-xl transition-all shadow-lg shadow-crimson-900/20 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed group"
            >
              <span class="flex items-center justify-center gap-2">
                {{ loading() ? 'AUTHENTICATING...' : 'ENTER THE VOID' }}
                <lucide-icon name="ChevronRight" class="w-4 h-4 group-hover:translate-x-1 transition-transform"></lucide-icon>
              </span>
            </button>
          </form>

          <p class="text-center text-noir-500 text-sm mt-8">
            New seeker? 
            <a routerLink="/register" class="text-crimson-400 hover:text-crimson-300 font-bold transition-colors">Forge Identity</a>
          </p>
        </div>

        <!-- Footer -->
        <p class="text-center text-noir-700 text-[10px] mt-10 uppercase tracking-widest">
          © 2026 Student Skill Tracker. All rights surrendered.
        </p>
      </div>
    </div>
  `
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  email = signal('');
  password = signal('');
  loading = signal(false);
  error = signal<string | null>(null);

  onSubmit() {
    this.loading.set(true);
    this.error.set(null);

    const loginRequest = {
      email: this.email(),
      password: this.password()
    };
 
    this.authService.login(loginRequest).subscribe({
      next: (response) => {
        if (response && response.token) {
          this.router.navigate(['/dashboard']);
        } else {
          this.error.set('AUTHENTICATION FAILED. INCORRECT CIPHER.');
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('SYSTEM ERROR. CONNECTION TO ABYSS LOST.');
        this.loading.set(false);
      }
    });
  }
}
