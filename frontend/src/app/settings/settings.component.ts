import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { CompilerService } from '../core/compiler.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent implements OnInit {
  private compilerService = inject(CompilerService);
  private router = inject(Router);

  isStatusLoading = signal(false);
  isConnected = signal(false);
  isConnecting = signal(false);
  isDisconnecting = signal(false);

  leetcodeSession = signal('');
  csrfToken = signal('');

  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  canConnect = computed(() => {
    return Boolean(
      this.leetcodeSession().trim() &&
      this.csrfToken().trim() &&
      !this.isConnecting()
    );
  });

  ngOnInit() {
    this.loadStatus();
  }

  loadStatus() {
    this.isStatusLoading.set(true);
    this.compilerService.getLeetCodeAuthStatus().subscribe({
      next: (status) => {
        this.isConnected.set(status.connected === true);
        this.isStatusLoading.set(false);
      },
      error: () => {
        this.isConnected.set(false);
        this.isStatusLoading.set(false);
      }
    });
  }

  connectLeetCode() {
    if (!this.canConnect()) {
      this.errorMessage.set('Both LEETCODE_SESSION and csrftoken are required.');
      return;
    }

    this.isConnecting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.compilerService.connectLeetCodeAuth({
      leetcodeSession: this.leetcodeSession().trim(),
      csrfToken: this.csrfToken().trim()
    }).subscribe({
      next: (status) => {
        this.isConnected.set(status.connected === true);
        this.isConnecting.set(false);
        this.leetcodeSession.set('');
        this.csrfToken.set('');
        this.successMessage.set('LeetCode connected. Credentials are stored securely on the backend.');
      },
      error: (err) => {
        this.isConnecting.set(false);
        this.errorMessage.set(err?.error?.error || 'Failed to connect LeetCode. Check values and try again.');
      }
    });
  }

  disconnectLeetCode() {
    if (this.isDisconnecting()) {
      return;
    }

    this.isDisconnecting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.compilerService.disconnectLeetCodeAuth().subscribe({
      next: (status) => {
        this.isConnected.set(status.connected === true);
        this.isDisconnecting.set(false);
        this.successMessage.set('LeetCode disconnected.');
      },
      error: (err) => {
        this.isDisconnecting.set(false);
        this.errorMessage.set(err?.error?.error || 'Failed to disconnect LeetCode.');
      }
    });
  }

  goToCompiler() {
    this.router.navigate(['/compiler']);
  }
}
