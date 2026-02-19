import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, RouterModule],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-noir-950 p-6">
      <div class="w-full max-w-md animate-fade-in">
        <!-- Brand -->
        <div class="flex flex-col items-center mb-10 text-center">
          <div class="w-16 h-16 rounded-2xl bg-zinc-100 flex items-center justify-center shadow-lg shadow-zinc-500/10 mb-4 animate-float">
            <lucide-icon name="Package" class="w-10 h-10 text-noir-950"></lucide-icon>
          </div>
          <h1 class="text-3xl font-bold tracking-tighter text-white uppercase">FORGE IDENTITY</h1>
          <p class="text-noir-400 mt-2 font-mono">Create your digital avatar.</p>
        </div>

        <!-- Form Card -->
        <div class="noir-card p-8">
          <form (ngSubmit)="onSubmit()" #registerForm="ngForm" class="space-y-4">
            
            <div class="space-y-2">
              <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">True Name</label>
              <div class="relative">
                <lucide-icon name="User" class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-noir-500"></lucide-icon>
                <input 
                  type="text" 
                  name="name"
                  [ngModel]="student().name" 
                  (ngModelChange)="updateField('name', $event)"
                  required
                  placeholder="Nietzsche Jr."
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 pl-10 pr-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono"
                >
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">Email Abyss</label>
              <div class="relative">
                <lucide-icon name="Mail" class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-noir-500"></lucide-icon>
                <input 
                  type="email" 
                  name="email"
                  [ngModel]="student().email" 
                  (ngModelChange)="updateField('email', $event)"
                  required
                  placeholder="name@example.com"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 pl-10 pr-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono"
                >
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">LeetCode Username</label>
              <div class="relative">
                <lucide-icon name="Layout" class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-noir-500"></lucide-icon>
                <input 
                  type="text" 
                  name="leetcodeUsername"
                  [ngModel]="student().leetcodeUsername" 
                  (ngModelChange)="updateField('leetcodeUsername', $event)"
                  required
                  placeholder="LeetKing"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 pl-10 pr-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono"
                >
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">Password</label>
                <input 
                  type="password" 
                  name="password"
                  [ngModel]="student().password" 
                  (ngModelChange)="updateField('password', $event)"
                  required
                  placeholder="••••"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 px-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono text-sm"
                >
              </div>
              <div class="space-y-2">
                <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">Confirm</label>
                <input 
                  type="password" 
                  name="confirmPassword"
                  [ngModel]="student().confirmPassword" 
                  (ngModelChange)="updateField('confirmPassword', $event)"
                  required
                  placeholder="••••"
                  class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 px-4 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono text-sm"
                >
              </div>
            </div>

            <div *ngIf="error()" class="p-3 bg-crimson-500/10 border border-crimson-500/20 rounded-lg text-crimson-400 text-xs font-mono">
              {{ error() }}
            </div>

            <button 
              type="submit"
              [disabled]="loading() || !registerForm.valid"
              class="w-full bg-zinc-100 hover:bg-white text-noir-950 font-bold py-3 rounded-xl transition-all shadow-lg shadow-zinc-950/20 disabled:opacity-50 mt-4 uppercase tracking-[0.2em] text-xs"
            >
              {{ loading() ? 'FORGING...' : 'INITIATE ASCENT' }}
            </button>
          </form>

          <p class="text-center text-noir-500 text-xs mt-6">
            Already initiated? 
            <a routerLink="/login" class="text-white hover:underline font-bold">Return to Void</a>
          </p>
        </div>
      </div>
    </div>
  `
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  student = signal({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    leetcodeUsername: ''
  });

  loading = signal(false);
  error = signal<string | null>(null);

  updateField(field: string, value: any) {
    this.student.update(s => ({ ...s, [field]: value }));
  }

  onSubmit() {
    const s = this.student();
    if (s.password !== s.confirmPassword) {
      this.error.set('PASSWORDS DO NOT MATCH.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.authService.register(s).subscribe({
      next: () => {
        this.router.navigate(['/login'], { queryParams: { registered: true } });
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'FORGE FAILED. TRY AGAIN.');
        this.loading.set(false);
      }
    });
  }
}
