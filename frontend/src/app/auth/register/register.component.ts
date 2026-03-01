import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
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

            <div class="space-y-4">
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-2">
                  <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">Password</label>
                  <div class="relative">
                    <input 
                      [type]="showPassword() ? 'text' : 'password'" 
                      name="password"
                      [ngModel]="student().password" 
                      (ngModelChange)="updateField('password', $event)"
                      required
                      placeholder="••••"
                      class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 pl-4 pr-16 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono text-sm"
                    >
                    <button
                      type="button"
                      (click)="showPassword.set(!showPassword())"
                      class="absolute right-2 top-1/2 -translate-y-1/2 text-[9px] font-bold uppercase tracking-wider text-noir-400 hover:text-noir-200"
                      [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                    >
                      {{ showPassword() ? 'Hide' : 'Show' }}
                    </button>
                  </div>
                </div>
                <div class="space-y-2">
                  <label class="text-[10px] font-bold uppercase tracking-[0.2em] text-noir-500 ml-1">Confirm</label>
                  <div class="relative">
                    <input 
                      [type]="showConfirmPassword() ? 'text' : 'password'" 
                      name="confirmPassword"
                      [ngModel]="student().confirmPassword" 
                      (ngModelChange)="updateField('confirmPassword', $event)"
                      required
                      placeholder="••••"
                      class="w-full bg-noir-900 border border-noir-800 rounded-xl py-2.5 pl-4 pr-16 text-noir-100 placeholder:text-noir-600 focus:outline-none focus:border-zinc-500/50 focus:ring-1 focus:ring-zinc-500/50 transition-all font-mono text-sm"
                    >
                    <button
                      type="button"
                      (click)="showConfirmPassword.set(!showConfirmPassword())"
                      class="absolute right-2 top-1/2 -translate-y-1/2 text-[9px] font-bold uppercase tracking-wider text-noir-400 hover:text-noir-200"
                      [attr.aria-label]="showConfirmPassword() ? 'Hide confirm password' : 'Show confirm password'"
                    >
                      {{ showConfirmPassword() ? 'Hide' : 'Show' }}
                    </button>
                  </div>
                </div>
              </div>

              <!-- Password Strength Meter -->
              <div *ngIf="student().password" class="space-y-3 animate-fade-in">
                <div class="flex gap-1 h-1">
                  <div 
                    *ngFor="let i of [1,2,3,4,5]" 
                    class="flex-1 rounded-full transition-all duration-500"
                    [class.bg-noir-800]="passwordStrength().score < i"
                    [class.bg-crimson-500]="passwordStrength().score >= i && passwordStrength().score <= 2"
                    [class.bg-amber-500]="passwordStrength().score >= i && passwordStrength().score > 2 && passwordStrength().score <= 4"
                    [class.bg-emerald-500]="passwordStrength().score >= i && passwordStrength().score === 5"
                  ></div>
                </div>
                
                <div class="grid grid-cols-2 gap-x-4 gap-y-1.5">
                  <div class="flex items-center gap-2 text-[9px] uppercase tracking-wider font-mono" 
                       [class.text-emerald-500]="passwordStrength().hasLength" [class.text-noir-600]="!passwordStrength().hasLength">
                    <lucide-icon [name]="passwordStrength().hasLength ? 'CheckCircle2' : 'Circle'" class="w-3 h-3"></lucide-icon>
                    8+ Characters
                  </div>
                  <div class="flex items-center gap-2 text-[9px] uppercase tracking-wider font-mono"
                       [class.text-emerald-500]="passwordStrength().hasUpper" [class.text-noir-600]="!passwordStrength().hasUpper">
                    <lucide-icon [name]="passwordStrength().hasUpper ? 'CheckCircle2' : 'Circle'" class="w-3 h-3"></lucide-icon>
                    Uppercase
                  </div>
                  <div class="flex items-center gap-2 text-[9px] uppercase tracking-wider font-mono"
                       [class.text-emerald-500]="passwordStrength().hasLower" [class.text-noir-600]="!passwordStrength().hasLower">
                    <lucide-icon [name]="passwordStrength().hasLower ? 'CheckCircle2' : 'Circle'" class="w-3 h-3"></lucide-icon>
                    Lowercase
                  </div>
                  <div class="flex items-center gap-2 text-[9px] uppercase tracking-wider font-mono"
                       [class.text-emerald-500]="passwordStrength().hasNumber" [class.text-noir-600]="!passwordStrength().hasNumber">
                    <lucide-icon [name]="passwordStrength().hasNumber ? 'CheckCircle2' : 'Circle'" class="w-3 h-3"></lucide-icon>
                    Number
                  </div>
                  <div class="flex items-center gap-2 text-[9px] uppercase tracking-wider font-mono"
                       [class.text-emerald-500]="passwordStrength().hasSpecial" [class.text-noir-600]="!passwordStrength().hasSpecial">
                    <lucide-icon [name]="passwordStrength().hasSpecial ? 'CheckCircle2' : 'Circle'" class="w-3 h-3"></lucide-icon>
                    Special Char
                  </div>
                </div>
              </div>
            </div>

            <div *ngIf="error()" class="p-3 bg-crimson-500/10 border border-crimson-500/20 rounded-lg text-crimson-400 text-xs font-mono">
              {{ error() }}
            </div>

            <button 
              type="submit"
              [disabled]="loading() || !registerForm.valid || passwordStrength().score < 5"
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
  showPassword = signal(false);
  showConfirmPassword = signal(false);

  passwordStrength = computed(() => {
    const p = this.student().password;
    const hasLength = p.length >= 8;
    const hasUpper = /[A-Z]/.test(p);
    const hasLower = /[a-z]/.test(p);
    const hasNumber = /[0-9]/.test(p);
    const hasSpecial = /[@#$%^&+=!]/.test(p);
    
    let score = 0;
    if (hasLength) score++;
    if (hasUpper) score++;
    if (hasLower) score++;
    if (hasNumber) score++;
    if (hasSpecial) score++;

    return { score, hasLength, hasUpper, hasLower, hasNumber, hasSpecial };
  });

  updateField(field: string, value: any) {
    this.student.update(s => ({ ...s, [field]: value }));
  }

  onSubmit() {
    const s = this.student();
    if (s.password !== s.confirmPassword) {
      this.error.set('PASSWORDS DO NOT MATCH.');
      return;
    }

    if (this.passwordStrength().score < 5) {
      this.error.set('PASSWORD IS NOT STRONG ENOUGH.');
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
